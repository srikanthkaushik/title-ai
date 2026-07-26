package com.marion.dmv.agent;

import com.marion.dmv.mcp.McpToolService;
import com.marion.dmv.retrieval.RetrievalResult;
import com.marion.dmv.retrieval.RetrievalService;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import org.bsc.langgraph4j.CompileConfig;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphDefinition;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.StateGraph;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.bsc.langgraph4j.GraphDefinition.END;
import static org.bsc.langgraph4j.GraphDefinition.START;
import static org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

@Configuration
public class TransferAgentGraph {

    static final String SYSTEM_PROMPT = """
            You are a Marion DMV title transfer assistant helping an examiner determine requirements
            for an out-of-state vehicle title transfer into Marion.

            You will be given retrieved regulatory documents and, if available, vehicle record data
            and database lookup results. Use ONLY the provided context — do not use general knowledge
            about real states or vehicles.

            STEP 1 — BRAND AND LIEN CHECK (do this before anything else):
            Scan the question AND any DATABASE LOOKUP RESULTS for these trigger words:
              Active lien, unreleased lien, lien holder → supervisorReferral = true
              Rebuilt, Reconstructed, Salvage, Junk, Flood, Water Damage, Odometer, Lemon Law,
              Salvage Rebuilt, or ANY other brand stamp → supervisorReferral = true
            Examples that MUST trigger supervisorReferral=true:
              "title with the brand 'Rebuilt'" → supervisorReferral = true
              "shows an active lien" → supervisorReferral = true
              "Junk brand on a Halloway title" → supervisorReferral = true
            If any trigger is found, set supervisorReferral=true, referralForm="TR-10",
            checklist=null, taxOwed=null, and populate conditionalChecklist. Stop normal processing.

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
              model year is less than 25 years old (relative to current year). Whether the origin
              state has an emissions program is irrelevant.
            - DATABASE LOOKUP RESULTS (if provided) are authoritative. Prefer them over retrieved
              documents when there is a conflict.

            Respond with a JSON object in EXACTLY this format — no markdown, no code fences, no comments.
            Start with { and end with }:

            {
              "reasoning": "Step-by-step analysis of the scenario",
              "supervisorReferral": false,
              "referralReason": null,
              "referralForm": null,
              "checklist": ["Origin title (Crestwood paper)", "Form TR-1", "Form TR-2 (VIN inspection)", "Bill of sale", "Proof of insurance", "Odometer disclosure (Form OD-1)", "Fee payment"],
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
            ChatModel chatModel) throws GraphStateException {

        CompiledGraph<TransferAgentState> compiled = new StateGraph<>(TransferAgentState::new)

                .addNode("retrieve", node_async(state -> {
                    List<RetrievalResult> hits = retrievalService.retrieveAndRerank(state.question());
                    return Map.of("context", buildContext(hits));
                }))

                .addNode("tool_fetch", node_async(state -> {
                    Map<String, String> toolData = new LinkedHashMap<>();
                    state.vehicleVin()
                            .flatMap(mcpToolService::lookupTitleLien)
                            .ifPresent(r -> toolData.put("VEHICLE_RECORD", r));
                    state.originState()
                            .flatMap(mcpToolService::lookupTaxReciprocity)
                            .ifPresent(r -> toolData.put("TAX_RECIPROCITY", r));
                    if (state.transferType().isPresent() && state.county().isPresent()) {
                        mcpToolService.lookupFees(state.transferType().get(), state.county().get())
                                .ifPresent(r -> toolData.put("FEE_SCHEDULE", r));
                    }
                    return Map.of("toolData", formatToolData(toolData));
                }))

                .addNode("generate", node_async(state -> {
                    String userPrompt = buildUserPrompt(state);
                    String answer = chatModel.chat(
                            List.of(SystemMessage.from(SYSTEM_PROMPT), UserMessage.from(userPrompt))
                    ).aiMessage().text();
                    return Map.of(
                            "draftAnswer", answer,
                            "cycleCount", state.cycleCount() + 1
                    );
                }))

                .addEdge(START, "retrieve")
                .addEdge("retrieve", "tool_fetch")
                .addEdge("tool_fetch", "generate")
                .addConditionalEdges("generate",
                        edge_async(state -> isStructurallyValid(state.draftAnswer()) || state.cycleCount() >= 2
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

    private static final Pattern BRAND_PATTERN = Pattern.compile("\"brand\"\\s*:\\s*\"([^\"]+)\"");

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
        }

        state.vehicleVin().ifPresent(v -> sb.append("VEHICLE VIN: ").append(v).append("\n"));
        state.originState().ifPresent(o -> sb.append("ORIGIN STATE: ").append(o).append("\n"));
        state.county().ifPresent(c -> sb.append("REGISTRATION COUNTY: ").append(c).append("\n"));
        state.transferType().ifPresent(t -> sb.append("TRANSFER TYPE: ").append(t).append("\n"));
        sb.append("\nQUESTION: ").append(state.question());

        if (state.cycleCount() > 0) {
            sb.append("\n\n[RETRY ").append(state.cycleCount())
              .append("] Your previous response did not produce valid JSON. ")
              .append("Output ONLY a JSON object — no markdown, no code fences, no // comments. ")
              .append("Start immediately with { and end with }.");
        }

        return sb.toString();
    }

    /**
     * Lightweight structural check — does not validate correctness, only shape.
     * Returns false only when the response is clearly malformed (missing required fields
     * or not JSON-like), triggering a GENERATE retry.
     */
    private static boolean isStructurallyValid(String draft) {
        if (draft == null || draft.isBlank()) {
            return false;
        }
        int open = draft.indexOf('{');
        int close = draft.lastIndexOf('}');
        if (open < 0 || close <= open) {
            return false;
        }
        return draft.contains("\"supervisorReferral\"") && draft.contains("\"reasoning\"");
    }
}
