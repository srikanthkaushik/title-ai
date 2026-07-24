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

            IMPORTANT RULES:
            - Brand equivalency: always consult the Brand Equivalency Guide for the specific origin state.
              Two states can use the same brand word with different Marion equivalents.
            - Tax computation: Marion rate is 5.5%. Apply reciprocity credit only if origin state has
              an agreement. Pembrook has NO reciprocity.
            - Emissions: required if registration county is metro (Marion, Riverside, Capital) AND
              model year is less than 25 years old (relative to current year). Whether the origin
              state has an emissions program is irrelevant.
            - Supervisor referral: required for any active lien, any branded title, any unrecognized
              origin state, or missing origin documentation. When referring, set supervisorReferral
              to true and set taxOwed to null.
            - DATABASE LOOKUP RESULTS (if provided under that heading) are authoritative system-of-record
              data. Prefer them over retrieved documents when there is a conflict.

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

    private static String buildUserPrompt(TransferAgentState state) {
        StringBuilder sb = new StringBuilder();
        sb.append("RETRIEVED CONTEXT:\n").append(state.context()).append("\n");

        String toolData = state.toolData();
        if (!toolData.isBlank()) {
            sb.append("DATABASE LOOKUP RESULTS (authoritative — prefer over context if different):\n");
            sb.append(toolData).append("\n");
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
