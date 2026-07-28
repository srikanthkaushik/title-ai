package com.marion.dmv.agent;

import com.marion.dmv.mcp.McpToolService;
import com.marion.dmv.retrieval.RetrievalResult;
import com.marion.dmv.retrieval.RetrievalService;
import com.marion.dmv.transfer.TransferResponseParser;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.bsc.langgraph4j.CompileConfig;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphDefinition;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.StateGraph;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.bsc.langgraph4j.GraphDefinition.END;
import static org.bsc.langgraph4j.GraphDefinition.START;
import static org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

@Configuration
public class TransferAgentGraph {

    private static final Logger log = LoggerFactory.getLogger(TransferAgentGraph.class);

    static final String SYSTEM_PROMPT = """
            You are a Marion DMV title transfer assistant helping an examiner determine requirements
            for an out-of-state vehicle title transfer into Marion.

            You will be given retrieved regulatory documents and, if available, vehicle record data
            and database lookup results. Use ONLY the provided context — do not use general knowledge
            about real states or vehicles.

            STEP 1 — EXCEPTION GATE (do this before anything else):
            Route to supervisor referral when ANY of the following is true:
              (a) Active lien, unreleased lien, or lien holder named → supervisorReferral = true
              (b) Any title brand (Rebuilt, Reconstructed, Salvage, Junk, Flood, Water Damage,
                  Odometer, Lemon Law, Salvage Rebuilt, or ANY other brand stamp) → supervisorReferral = true
              (c) Origin state is NOT one of Marion's four recognized states (Verdana, Crestwood,
                  Halloway, Pembrook) → supervisorReferral = true
            Examples that MUST trigger supervisorReferral=true:
              "title with the brand 'Rebuilt'" → supervisorReferral = true
              "shows an active lien" → supervisorReferral = true
              "Junk brand on a Halloway title" → supervisorReferral = true
              "title from the state of Westbrook" → supervisorReferral = true (unrecognized state)
            If any trigger is found, set supervisorReferral=true, referralForm="TR-10",
            checklist=null, taxOwed=null, and populate conditionalChecklist. Stop normal processing.
            Before stopping: also identify the Marion brand equivalent from the Brand Equivalency Guide
            and include it in referralReason (e.g., "Halloway Rebuilt → Marion brand: Reconstructed").
            SCANNING CAUTION: An `odometer` or `gvwr_lbs` field in VEHICLE_RECORD is a data field
            (mileage or weight) — it is NOT a brand trigger. Only a `brand` field explicitly containing
            a brand name (Odometer, Rebuilt, etc.) triggers this step. Expired insurance is not a trigger.

            STEP 2 — NORMAL PROCESSING (only if Step 1 found no triggers):
            - Brand equivalency: consult the Brand Equivalency Guide for the specific origin state.
              Two states can use the same brand word with different Marion equivalents.
            - Tax computation (apply EXACTLY in order — do not skip or shortcut steps):
                1. taxable_value = declared purchase price (or NADA clean retail if higher for PURCHASE;
                   NADA clean retail for RELOCATION). Use the value stated in the question.
                2. marion_tax_due = taxable_value × 5.5%
                3. Determine origin_rate:
                     - Use the rate from TAX_RECIPROCITY in DATABASE LOOKUP RESULTS if available.
                     - OR use the rate explicitly stated in the question (e.g., "paid 6% in Crestwood"
                       → origin_rate = 6%; "paid 4.5% in Halloway" → origin_rate = 4.5%).
                   If origin state has a reciprocity agreement:
                     tax_paid_in_origin = taxable_value × origin_rate
                     reciprocity_credit = min(tax_paid_in_origin, marion_tax_due)
                   Else (no reciprocity agreement): reciprocity_credit = 0
                4. taxOwed = max(0, marion_tax_due - reciprocity_credit)
                   Round to the nearest cent. If credit >= marion_tax_due, taxOwed = 0 (no refund).
                   WARNING: Do NOT compute taxOwed as (Marion_rate − origin_rate) × value.
                   That formula is WRONG. Use steps 2-4 above.
              Pembrook has NO reciprocity agreement — credit is always $0, full Marion tax applies.
              Example A (Crestwood, $15,000, 6% rate, reciprocity): marion_tax_due = $825,
                tax_paid_in_origin = $900, credit = min($900,$825) = $825, taxOwed = $0.
              Example B (Halloway, $18,000, 4.5% rate, reciprocity): marion_tax_due = $990,
                tax_paid_in_origin = $810, credit = min($810,$990) = $810, taxOwed = $180.
              Example C (Verdana, $20,000, 5% rate, reciprocity): marion_tax_due = $1,100,
                tax_paid_in_origin = $1,000, credit = min($1,000,$1,100) = $1,000,
                taxOwed = $100. (5% is LESS than 5.5% so credit < Marion tax — owe the difference.)
              IMPORTANT: taxOwed is ADDITIONAL SALES TAX ONLY — never include title fees,
              VIN fees, or registration fees in taxOwed. Those belong in the "fees" object.
            - Emissions: required if registration county is metro (Marion, Riverside, Capital) AND
              (current_year - model_year) < 25. Compute the age explicitly:
              age = current_year - model_year. If age < 25 → REQUIRED; if age >= 25 → exempt.
              Example: current year 2026, model year 2003 → 2026 - 2003 = 23 < 25 → REQUIRED.
              Example: current year 2026, model year 1998 → 2026 - 1998 = 28 >= 25 → exempt.
              CURRENT RULE: 25-year threshold (effective Jan 2023). If a retrieved document
              says "20-year threshold" — that version is SUPERSEDED; do NOT use it.
              The origin state's emissions program is irrelevant — Marion's rules govern.
              When emissions are REQUIRED, add "Emissions inspection (Form EMIT-1) — paid to
              authorized testing station" to the checklist.
            - RELOCATION vs. PURCHASE — the checklist differs by transfer type:
              PURCHASE (customer bought the vehicle):
                · Include "Bill of sale" in the checklist
                · Tax basis = declared purchase price (or NADA clean retail if higher)
              RELOCATION (owner already owns vehicle; moved to Marion — no new purchase):
                · Include "Proof of Marion residency — any TWO of: signed lease or mortgage
                  statement; utility bill within 60 days; employer letter on letterhead within 60 days"
                · Do NOT include "Bill of sale" — no purchase transaction occurred
                · Tax basis = NADA clean retail value (not the original purchase price)
                · Form TR-1 Block C must indicate RELOCATION; Block D is left blank
            - DATABASE LOOKUP RESULTS (if provided) are authoritative. Prefer them over retrieved
              documents when there is a conflict.

            Respond with a JSON object in EXACTLY this format — no markdown, no code fences, no comments.
            Start with { and end with }:

            {
              "reasoning": "Step-by-step analysis of the scenario",
              "supervisorReferral": false,
              "referralReason": null,
              "referralForm": null,
              "checklist": ["Origin title (Crestwood paper)", "Form TR-1", "Form TR-2 (VIN inspection)", "Bill of sale", "Proof of insurance", "Odometer disclosure (Form OD-1)", "Emissions inspection (Form EMIT-1) — paid to authorized testing station", "Fee payment"],
              "conditionalChecklist": null,
              "conditionalNote": null,
              "fees": {
                "titleFee": 25.00,
                "vinFee": 15.00,
                "registrationFee": 45.00,
                "emissionsFee": 0,
                "lienReleaseFee": 0,
                "totalToDMV": 85.00
              },
              "taxOwed": 0.00,
              "sources": ["procedure-ch4-1-purchase-paper-no-lien.md §4.1", "admin-rule-9-fee-schedule.md"]
            }

            When supervisorReferral is true:
            - Set checklist to null
            - Set referralForm to "TR-10"
            - Populate conditionalChecklist with items the customer will likely need once resolved
            - Set conditionalNote to: "CONDITIONAL — SUBJECT TO SUPERVISOR REVIEW. DO NOT ACT ON THIS
              LIST UNTIL THE SUPERVISOR HAS CLEARED YOUR TRANSACTION."
            - Set taxOwed to null
            """;

    @Bean
    public CompiledGraph<TransferAgentState> compiledTransferGraph(
            RetrievalService retrievalService,
            McpToolService mcpToolService,
            ChatModel chatModel,
            MeterRegistry meterRegistry) throws GraphStateException {

        Timer retrieveTimer  = Timer.builder("marion.agent.node").tag("node", "retrieve").register(meterRegistry);
        Timer toolFetchTimer = Timer.builder("marion.agent.node").tag("node", "tool-fetch").register(meterRegistry);
        Timer generateTimer  = Timer.builder("marion.agent.node").tag("node", "generate").register(meterRegistry);

        CompiledGraph<TransferAgentState> compiled = new StateGraph<>(TransferAgentState::new)

                .addNode("retrieve", node_async(state ->
                    retrieveTimer.record(() -> {
                        List<RetrievalResult> hits = retrievalService.retrieveAndRerank(state.question());
                        return Map.of("context", buildContext(hits));
                    })
                ))

                .addNode("tool_fetch", node_async(state ->
                    toolFetchTimer.record(() -> {
                        Map<String, String> toolData = new LinkedHashMap<>();
                        state.vehicleVin()
                                .flatMap(mcpToolService::lookupTitleLien)
                                .ifPresent(r -> toolData.put("VEHICLE_RECORD", r));
                        state.originState().ifPresent(os -> {
                            Optional<String> result = mcpToolService.lookupTaxReciprocity(os);
                            log.debug("[TOOL_FETCH] lookupTaxReciprocity({}) -> {}", os,
                                    result.map(r -> r.substring(0, Math.min(r.length(), 120))).orElse("EMPTY"));
                            result.ifPresent(r -> toolData.put("TAX_RECIPROCITY", r));
                        });
                        if (state.transferType().isPresent() && state.county().isPresent()) {
                            mcpToolService.lookupFees(state.transferType().get(), state.county().get())
                                    .ifPresent(r -> toolData.put("FEE_SCHEDULE", r));
                        }
                        log.debug("[TOOL_FETCH] toolData keys: {}", toolData.keySet());
                        return Map.of("toolData", formatToolData(toolData));
                    })
                ))

                .addNode("generate", node_async(state ->
                    generateTimer.record(() -> {
                        String userPrompt = buildUserPrompt(state);
                        String answer = chatModel.chat(
                                List.of(SystemMessage.from(SYSTEM_PROMPT), UserMessage.from(userPrompt))
                        ).aiMessage().text();

                        Map<String, Object> updates = new HashMap<>();
                        updates.put("draftAnswer", answer);
                        updates.put("cycleCount", state.cycleCount() + 1);
                        try {
                            TransferResponseParser.parse(answer);
                            updates.put("parseError", "");
                        } catch (IllegalArgumentException e) {
                            updates.put("parseError", e.getMessage());
                        }
                        return updates;
                    })
                ))

                .addEdge(START, "retrieve")
                .addEdge("retrieve", "tool_fetch")
                .addEdge("tool_fetch", "generate")
                .addConditionalEdges("generate",
                        edge_async(state -> state.parseError().isEmpty() || state.cycleCount() >= 2
                                ? "end" : "generate"),
                        Map.of("generate", "generate", "end", END))

                // recursionLimit is the backstop: 3 nodes × 2 cycles + retrieve + tool_fetch = 8
                .compile(CompileConfig.builder().recursionLimit(10).build());

        return compiled;
    }

    private static String buildContext(List<RetrievalResult> hits) {
        if (hits.isEmpty()) {
            return "(No relevant documents retrieved)";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < hits.size(); i++) {
            RetrievalResult h = hits.get(i);
            sb.append("--- [").append(i + 1).append("] ").append(h.source()).append(" ---\n");
            sb.append(h.text()).append("\n\n");
        }
        return sb.toString();
    }

    private static String formatToolData(Map<String, String> toolData) {
        if (toolData.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        toolData.forEach((key, value) -> sb.append(key).append(": ").append(value).append("\n"));
        return sb.toString();
    }

    private static final Pattern BRAND_PATTERN            = Pattern.compile("\"brand\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern RATE_PATTERN             = Pattern.compile("\"origin_rate_pct\"\\s*:\\s*([\\d\\.]+)");
    private static final Pattern AGREEMENT_PATTERN        = Pattern.compile("\"has_agreement\"\\s*:\\s*(true|false)");
    private static final Pattern BRAND_IN_QUESTION_PATTERN = Pattern.compile(
            "\\b(?:Junk|Rebuilt|Salvage|Reconstructed|Flood|Odometer|Lemon Law|Water Damage)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final double  MARION_TAX_RATE_PCT = 5.5;

    private static String buildUserPrompt(TransferAgentState state) {
        StringBuilder sb = new StringBuilder();
        sb.append("RETRIEVED CONTEXT:\n").append(state.context()).append("\n");

        String toolData = state.toolData();
        if (!toolData.isBlank()) {
            sb.append("DATABASE LOOKUP RESULTS (authoritative — prefer over context if different):\n");
            sb.append(toolData).append("\n");

            // Explicitly surface brand field so STEP 1 fires reliably
            Matcher m = BRAND_PATTERN.matcher(toolData);
            if (m.find()) {
                sb.append("*** BRAND STAMP DETECTED IN VEHICLE RECORD: \"").append(m.group(1))
                  .append("\" — This is a BRANDED TITLE. Per STEP 1: supervisorReferral=true REQUIRED. ***\n");
            }

            // Lock both rates and pre-compute intermediates so the model cannot substitute a wrong rate
            Matcher rm  = RATE_PATTERN.matcher(toolData);
            Matcher agm = AGREEMENT_PATTERN.matcher(toolData);
            if (rm.find() && agm.find()) {
                double originRate   = Double.parseDouble(rm.group(1));
                boolean hasAgreement = Boolean.parseBoolean(agm.group(1));
                log.debug("[RATE_BANNER] fired — originRate={}% agreement={}", originRate, hasAgreement);
                sb.append("*** TAX COMPUTATION ANCHORS (authoritative — use these exact values, no substitutions):\n");
                sb.append("    Marion tax rate : ").append(MARION_TAX_RATE_PCT).append("%  (FIXED — never use any other rate for Marion)\n");
                sb.append("    Origin tax rate : ").append(originRate).append("%  (from database — use THIS for tax_paid_in_origin)\n");
                sb.append("    Reciprocity     : ").append(hasAgreement ? "YES — credit applies" : "NO — credit = $0, full Marion tax owed").append("\n");
                sb.append("    Formula         : marion_tax_due = taxable_value × ").append(MARION_TAX_RATE_PCT).append("%\n");
                if (hasAgreement) {
                    sb.append("                      tax_paid_in_origin = taxable_value × ").append(originRate).append("%\n");
                    sb.append("                      reciprocity_credit = min(tax_paid_in_origin, marion_tax_due)\n");
                    sb.append("                      taxOwed = max(0, marion_tax_due − reciprocity_credit)\n");
                } else {
                    sb.append("                      taxOwed = marion_tax_due  (no credit)\n");
                }
                sb.append("    WARNING: Do NOT use the shortcut (Marion_rate − origin_rate) × value — that formula is WRONG. ***\n");
            } else {
                log.debug("[RATE_BANNER] did not fire — toolData: {}", toolData);
            }
        }

        state.vehicleVin().ifPresent(v -> sb.append("VEHICLE VIN: ").append(v).append("\n"));
        state.originState().ifPresent(o -> sb.append("ORIGIN STATE: ").append(o).append("\n"));
        state.county().ifPresent(c -> sb.append("REGISTRATION COUNTY: ").append(c).append("\n"));
        state.transferType().ifPresent(t -> sb.append("TRANSFER TYPE: ").append(t).append("\n"));

        // Brand mentioned in question text (no VIN → no vehicle record banner fires)
        Matcher qb = BRAND_IN_QUESTION_PATTERN.matcher(state.question());
        if (qb.find()) {
            sb.append("*** BRAND TERM '").append(qb.group()).append("' DETECTED IN QUESTION — ");
            sb.append("consult the Brand Equivalency Guide for the origin state ");
            sb.append("and include the Marion brand equivalent in referralReason ");
            sb.append("(e.g., 'Halloway Junk → Marion brand: Salvage'). ***\n");
        }

        sb.append("\nQUESTION: ").append(state.question());

        if (state.cycleCount() > 0) {
            sb.append("\n\n[RETRY ").append(state.cycleCount()).append("] ");
            String err = state.parseError();
            if (!err.isBlank()) {
                sb.append("Your previous response could not be parsed. Error: ").append(err).append(" ");
            } else {
                sb.append("Your previous response did not produce valid JSON. ");
            }
            sb.append("Output ONLY a JSON object — no markdown, no code fences, no // comments. ")
              .append("Start immediately with { and end with }.");
        }

        return sb.toString();
    }

}
