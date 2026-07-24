package com.marion.dmv.transfer;

import com.marion.dmv.retrieval.RetrievalResult;
import com.marion.dmv.retrieval.RetrievalService;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;

@RestController
@RequestMapping("/api/transfer")
public class TransferController {

    private static final String SYSTEM_PROMPT = """
            You are a Marion DMV title transfer assistant helping an examiner determine requirements
            for an out-of-state vehicle title transfer into Marion.

            You will be given retrieved regulatory documents and, if available, vehicle record data.
            Use ONLY the provided context — do not use general knowledge about real states or vehicles.

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

            Respond with a JSON object in EXACTLY this format — no markdown, no code fences.
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

    private final ChatModel chatModel;
    private final RetrievalService retrievalService;
    private final Timer answerTimer;

    public TransferController(ChatModel chatModel,
                              RetrievalService retrievalService,
                              MeterRegistry meterRegistry) {
        this.chatModel = chatModel;
        this.retrievalService = retrievalService;
        this.answerTimer = Timer.builder("marion.answer")
                .description("Answer generation time")
                .tag("node", "answer-generator")
                .register(meterRegistry);
    }

    // Uses non-streaming ChatModel so the full response is assembled before emitting.
    // Ollama's streaming API returns tokens without leading spaces (qwen tokenizer behaviour),
    // which causes words to concatenate. The non-streaming API returns correctly spaced text.
    // StreamingChatModel is still wired as a bean for the future LangGraph agent graph.
    @PostMapping(value = "/query", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> query(@RequestBody TransferRequest request) {
        List<RetrievalResult> hits = retrievalService.retrieveAndRerank(request.question());
        String context = buildContext(hits);
        String userPrompt = buildUserPrompt(request, context);

        return Mono.fromCallable(() ->
                answerTimer.record(() ->
                        chatModel.chat(
                                List.of(SystemMessage.from(SYSTEM_PROMPT), UserMessage.from(userPrompt))
                        ).aiMessage().text()
                )
        )
        .subscribeOn(Schedulers.boundedElastic())
        .flatMapMany(text -> Flux.just(text, "[DONE]"));
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

    private String buildUserPrompt(TransferRequest req, String context) {
        StringBuilder sb = new StringBuilder();
        sb.append("RETRIEVED CONTEXT:\n").append(context).append("\n");
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
        return sb.toString();
    }
}
