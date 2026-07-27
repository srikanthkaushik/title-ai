package com.marion.dmv.transfer;

import com.marion.dmv.mcp.McpToolService;
import com.marion.dmv.retrieval.RetrievalResult;
import com.marion.dmv.retrieval.RetrievalService;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/transfer")
public class TransferController {

    private static final String SYSTEM_PROMPT = """
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
            - DATABASE LOOKUP RESULTS (if provided) are authoritative. Prefer them over retrieved
              documents when there is a conflict.

            Respond with a JSON object in EXACTLY this format — no markdown, no code fences.
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

    private final ChatModel chatModel;
    private final RetrievalService retrievalService;
    private final McpToolService mcpToolService;
    private final Timer answerTimer;

    public TransferController(ChatModel chatModel,
                              RetrievalService retrievalService,
                              McpToolService mcpToolService,
                              MeterRegistry meterRegistry) {
        this.chatModel = chatModel;
        this.retrievalService = retrievalService;
        this.mcpToolService = mcpToolService;
        this.answerTimer = Timer.builder("marion.answer")
                .description("Answer generation time")
                .tag("node", "answer-generator")
                .register(meterRegistry);
    }

    // All blocking work (retrieval, MCP tool calls, LLM) runs on boundedElastic.
    // Uses non-streaming ChatModel so the full response is assembled before parsing.
    @PostMapping(value = "/query", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<TransferResponse> query(@RequestBody TransferRequest request) {
        return Mono.fromCallable(() -> {
            List<RetrievalResult> hits = retrievalService.retrieveAndRerank(request.question());
            String context = buildContext(hits);
            Map<String, String> toolData = fetchToolData(request);

            String raw = answerTimer.record(() ->
                    chatModel.chat(
                            List.of(SystemMessage.from(SYSTEM_PROMPT),
                                    UserMessage.from(buildUserPrompt(request, context, toolData, null)))
                    ).aiMessage().text()
            );

            try {
                return TransferResponseParser.parse(raw);
            } catch (IllegalArgumentException firstError) {
                String retryRaw = chatModel.chat(
                        List.of(SystemMessage.from(SYSTEM_PROMPT),
                                UserMessage.from(buildUserPrompt(request, context, toolData, firstError.getMessage())))
                ).aiMessage().text();
                return TransferResponseParser.parse(retryRaw);
            }
        })
        .subscribeOn(Schedulers.boundedElastic());
    }

    private Map<String, String> fetchToolData(TransferRequest req) {
        Map<String, String> data = new LinkedHashMap<>();

        if (req.vehicleVin() != null && !req.vehicleVin().isBlank()) {
            mcpToolService.lookupTitleLien(req.vehicleVin())
                    .ifPresent(r -> data.put("VEHICLE_RECORD", r));
        }
        if (req.originState() != null && !req.originState().isBlank()) {
            mcpToolService.lookupTaxReciprocity(req.originState())
                    .ifPresent(r -> data.put("TAX_RECIPROCITY", r));
        }
        if (req.transferType() != null && !req.transferType().isBlank()
                && req.county() != null && !req.county().isBlank()) {
            mcpToolService.lookupFees(req.transferType(), req.county())
                    .ifPresent(r -> data.put("FEE_SCHEDULE", r));
        }

        return data;
    }

    private String buildContext(List<RetrievalResult> hits) {
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

    private static final Pattern BRAND_PATTERN = Pattern.compile("\"brand\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern RATE_PATTERN = Pattern.compile("\"origin_rate_pct\"\\s*:\\s*([\\d\\.]+)");

    private String buildUserPrompt(TransferRequest req, String context, Map<String, String> toolData, String parseError) {
        StringBuilder sb = new StringBuilder();
        sb.append("RETRIEVED CONTEXT:\n").append(context).append("\n");

        if (!toolData.isEmpty()) {
            sb.append("DATABASE LOOKUP RESULTS (authoritative — prefer over context if different):\n");
            toolData.forEach((key, value) ->
                    sb.append(key).append(": ").append(value).append("\n"));

            // Explicitly surface brand field so STEP 1 fires reliably
            String vehicleRecord = toolData.get("VEHICLE_RECORD");
            if (vehicleRecord != null) {
                Matcher m = BRAND_PATTERN.matcher(vehicleRecord);
                if (m.find()) {
                    sb.append("*** BRAND STAMP DETECTED IN VEHICLE RECORD: \"").append(m.group(1))
                      .append("\" — This is a BRANDED TITLE. Per STEP 1: supervisorReferral=true REQUIRED. ***\n");
                }
            }

            // Surface the exact reciprocity rate so the model cannot hallucinate a different rate
            String reciprocity = toolData.get("TAX_RECIPROCITY");
            if (reciprocity != null) {
                Matcher rm = RATE_PATTERN.matcher(reciprocity);
                if (rm.find()) {
                    sb.append("*** ORIGIN TAX RATE (from database): ").append(rm.group(1))
                      .append("% — use THIS rate exactly for tax_paid_in_origin. Do not use any other rate. ***\n");
                }
            }
            sb.append("\n");
        }

        if (req.vehicleVin() != null) {
            sb.append("VEHICLE VIN: ").append(req.vehicleVin()).append("\n");
        }
        if (req.originState() != null) {
            sb.append("ORIGIN STATE: ").append(req.originState()).append("\n");
        }
        if (req.county() != null) {
            sb.append("REGISTRATION COUNTY: ").append(req.county()).append("\n");
        }
        if (req.transferType() != null) {
            sb.append("TRANSFER TYPE: ").append(req.transferType()).append("\n");
        }
        sb.append("\nQUESTION: ").append(req.question());

        if (parseError != null && !parseError.isBlank()) {
            sb.append("\n\n[RETRY] Your previous response could not be parsed. Error: ")
              .append(parseError)
              .append("\nOutput ONLY a JSON object — no markdown, no code fences, no // comments. ")
              .append("Start immediately with { and end with }.");
        }
        return sb.toString();
    }
}
