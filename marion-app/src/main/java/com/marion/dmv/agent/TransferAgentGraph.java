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

            STEP 0 — SCOPE CHECK (do this first):
            Determine whether the question describes a specific transfer scenario to evaluate, or is
            a general informational question (e.g., "how long does processing take?", "what is the
            fee for X?", "does Marion have reciprocity with Y?").
            - If INFORMATIONAL: answer the question in "reasoning" using only the retrieved context.
              Set checklist=null, conditionalChecklist=null, fees=null, taxOwed=null,
              supervisorReferral=false, referralReason=null, referralForm=null.
              Populate "sources" with any documents used. Do NOT fabricate a transfer checklist.
            - If TRANSFER SCENARIO: proceed to STEP 1.

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
            Before stopping: set referralReason to describe the SPECIFIC trigger(s) actually present
            in THIS question. Never copy a guide's example text verbatim, and never mention a brand
            equivalent unless trigger (b) is one of the reasons this case triggered:
              - Trigger (a) fired: name the lien holder/status from the question (e.g., "Active lien
                held by First National Bank — must be released before transfer").
              - Trigger (b) fired: this is the ONLY case where you consult the Brand Equivalency Guide
                and state the specific Marion brand equivalent for the origin state and brand actually
                named in the question (e.g., "Halloway 'Rebuilt' → Marion brand: Reconstructed" — but
                only if Halloway/Rebuilt is what THIS question actually says).
              - Trigger (c) fired: name the unrecognized origin state (e.g., "Origin state 'Westbrook'
                is not one of Marion's four recognized states").
              If more than one trigger fired, describe each one that applies.
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
    public ThreadTrackingMemorySaver checkpointSaver() {
        return new ThreadTrackingMemorySaver();
    }

    @Bean
    public CompiledGraph<TransferAgentState> compiledTransferGraph(
            RetrievalService retrievalService,
            McpToolService mcpToolService,
            ChatModel chatModel,
            MeterRegistry meterRegistry,
            ThreadTrackingMemorySaver checkpointSaver) throws GraphStateException {

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
                            var parsed = TransferResponseParser.parse(answer);
                            updates.put("parseError", "");
                            updates.put("supervisorReferral", parsed.supervisorReferral());
                        } catch (IllegalArgumentException e) {
                            updates.put("parseError", e.getMessage());
                        }
                        return updates;
                    })
                ))

                // Gate node: never runs any logic of its own. Its only purpose is a named
                // interruptBefore() target — the graph pauses here whenever GENERATE produced
                // a referral, so a human supervisor can review before the run reaches END.
                .addNode("await_supervisor", node_async(state -> Map.of()))

                .addEdge(START, "retrieve")
                .addEdge("retrieve", "tool_fetch")
                .addEdge("tool_fetch", "generate")
                .addConditionalEdges("generate",
                        edge_async(TransferAgentGraph::routeAfterGenerate),
                        Map.of("generate", "generate", "await", "await_supervisor", "end", END))
                // Resuming re-enters here and always falls through to a second GENERATE pass —
                // by the time this node's body runs, a resume has always supplied a decision
                // (see routeAfterGenerate: postReview short-circuits before "await" is ever reached again).
                .addEdge("await_supervisor", "generate")

                // recursionLimit is a backstop, not a tight budget: retrieve + tool_fetch + up to
                // FIRST_PASS_MAX_CYCLES generate cycles + await + up to POST_REVIEW_MAX_CYCLES more
                // generate cycles + end.
                .compile(CompileConfig.builder()
                        .recursionLimit(14)
                        .checkpointSaver(checkpointSaver)
                        .interruptBefore("await_supervisor")
                        .build());

        return compiled;
    }

    private static final int FIRST_PASS_MAX_CYCLES = 2;
    private static final int POST_REVIEW_MAX_CYCLES = 2;

    private static String routeAfterGenerate(TransferAgentState state) {
        boolean postReview = state.supervisorDecision().isPresent();
        int cycleCap = postReview
                ? FIRST_PASS_MAX_CYCLES + POST_REVIEW_MAX_CYCLES
                : FIRST_PASS_MAX_CYCLES;

        if (!state.parseError().isEmpty() && state.cycleCount() < cycleCap) {
            return "generate";
        }
        // Once a supervisor decision has been folded in, never re-pause — the decision already
        // happened. Only a fresh (non-postReview) referral routes to await_supervisor.
        if (!postReview && state.parseError().isEmpty() && state.supervisorReferral()) {
            return "await";
        }
        return "end";
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
    private static final Pattern ACTIVE_LIEN_PATTERN      = Pattern.compile("\"lien_status\"\\s*:\\s*\"ACTIVE\"");
    private static final Pattern LIENHOLDER_PATTERN       = Pattern.compile("\"lienholder_name\"\\s*:\\s*\"([^\"]+)\"");
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

            // Explicitly surface an active lien so STEP 1 fires reliably even when the only
            // signal is tool data, not question text — observed qwen2.5:7b missing a plain
            // "lien_status":"ACTIVE" field in VEHICLE_RECORD without a callout banner like this
            // one (it does reliably notice a brand, which already had this treatment).
            if (ACTIVE_LIEN_PATTERN.matcher(toolData).find()) {
                Matcher lh = LIENHOLDER_PATTERN.matcher(toolData);
                String holder = lh.find() ? lh.group(1) : "unspecified lienholder";
                sb.append("*** ACTIVE LIEN DETECTED IN VEHICLE RECORD: lienholder \"").append(holder)
                  .append("\" — Per STEP 1: supervisorReferral=true REQUIRED. ***\n");
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

        // Independent of any parse-retry below: whenever a supervisor decision has been merged
        // into state (i.e. this GENERATE call is the post-resume pass), always include it — even
        // on a parse-retry of the post-review pass itself, or the model loses the decision context.
        if (state.supervisorDecision().isPresent()) {
            sb.append("\n\n").append(buildSupervisorReviewBlock(state));
        }

        if (!state.parseError().isEmpty()) {
            sb.append("\n\n[RETRY ").append(state.cycleCount()).append("] ")
              .append("Your previous response could not be parsed. Error: ").append(state.parseError()).append(" ")
              .append("Output ONLY a JSON object — no markdown, no code fences, no // comments. ")
              .append("Start immediately with { and end with }.");
        }

        return sb.toString();
    }

    private static String buildSupervisorReviewBlock(TransferAgentState state) {
        StringBuilder sb = new StringBuilder();
        sb.append("*** SUPERVISOR REVIEW COMPLETE — this supersedes any STEP 1 trigger banners above;\n");
        sb.append("    do not re-evaluate STEP 1, the supervisor has already ruled on the exception. ***\n");
        sb.append("Decision: ").append(state.supervisorDecision().orElse("UNKNOWN")).append("\n");
        state.supervisorNote().filter(n -> !n.isBlank())
                .ifPresent(n -> sb.append("Supervisor note: \"").append(n).append("\"\n"));
        sb.append("\nYOUR PRIOR ANALYSIS (before supervisor review):\n").append(state.draftAnswer()).append("\n");
        sb.append("""

                A supervisor has reviewed the exception flagged in your prior analysis above and
                rendered the decision shown. Produce the FINAL response now, replacing your prior
                analysis entirely.

                If Decision is APPROVED:
                  - The referral exception is resolved — proceed with STEP 2 normal processing as if
                    it never blocked the transfer. Compute checklist, fees, and taxOwed exactly per
                    STEP 2, using the RETRIEVED CONTEXT and DATABASE LOOKUP RESULTS above.
                  - Move any items from your prior conditionalChecklist into "checklist", plus
                    anything else STEP 2 requires.
                  - Set supervisorReferral=false, referralForm=null, conditionalChecklist=null,
                    conditionalNote=null. You may leave referralReason as-is for the audit trail.
                  - In "reasoning", briefly note that supervisor approval was granted (reference the
                    supervisor note above if one was given).

                If Decision is DENIED:
                  - The transfer cannot proceed. Keep supervisorReferral=true, checklist=null,
                    taxOwed=null, conditionalChecklist=null.
                  - Set conditionalNote to explain the transfer was denied by the supervisor, quoting
                    their note if one was given.
                  - In "reasoning", state plainly that the supervisor denied the referral and why, so
                    the record is auditable.

                Output ONLY the JSON object in the schema already described above — no markdown, no
                commentary outside the JSON.
                """);
        return sb.toString();
    }

}
