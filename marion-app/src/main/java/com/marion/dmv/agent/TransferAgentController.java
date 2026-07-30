package com.marion.dmv.agent;

import com.marion.dmv.transfer.TransferRequest;
import com.marion.dmv.transfer.TransferResponse;
import com.marion.dmv.transfer.TransferResponseParser;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphInput;
import org.bsc.langgraph4j.RunnableConfig;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/transfer")
public class TransferAgentController {

    private static final String AWAIT_SUPERVISOR_NODE = "await_supervisor";

    private final CompiledGraph<TransferAgentState> graph;

    public TransferAgentController(CompiledGraph<TransferAgentState> graph) {
        this.graph = graph;
    }

    @PostMapping(value = "/query/agent", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<AgentTransferResponse> queryAgent(@RequestBody TransferRequest request) {
        Map<String, Object> inputs = buildInputs(request);
        String threadId = UUID.randomUUID().toString();
        RunnableConfig config = RunnableConfig.builder().threadId(threadId).build();

        return Mono.fromCallable(() -> {
            graph.invoke(inputs, config);
            return toAgentResponse(config, threadId);
        })
        .subscribeOn(Schedulers.boundedElastic());
    }

    // Resumes a run that paused at await_supervisor. The supervisor's decision/note are merged
    // into graph state and the run continues to END — it does not re-invoke the LLM.
    @PostMapping(value = "/query/agent/resume", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<AgentTransferResponse> resume(@RequestBody SupervisorDecisionRequest request) {
        RunnableConfig config = RunnableConfig.builder().threadId(request.threadId()).build();
        Map<String, Object> resumeData = new HashMap<>();
        resumeData.put("supervisorDecision", request.decision());
        resumeData.put("supervisorNote", request.note() == null ? "" : request.note());

        return Mono.fromCallable(() -> {
            graph.invoke(GraphInput.resume(resumeData), config);
            return toAgentResponse(config, request.threadId());
        })
        .subscribeOn(Schedulers.boundedElastic());
    }

    private AgentTransferResponse toAgentResponse(RunnableConfig config, String threadId) {
        var snapshot = graph.getState(config);
        String draftAnswer = snapshot.state().draftAnswer();
        if (draftAnswer.isBlank()) {
            throw new IllegalStateException("Agent graph produced no output");
        }
        TransferResponse response = TransferResponseParser.parse(draftAnswer);
        boolean awaitingSupervisor = AWAIT_SUPERVISOR_NODE.equals(snapshot.next());
        return new AgentTransferResponse(response, awaitingSupervisor, threadId);
    }

    private static Map<String, Object> buildInputs(TransferRequest req) {
        Map<String, Object> inputs = new HashMap<>();
        inputs.put("question", req.question());
        if (req.vehicleVin() != null && !req.vehicleVin().isBlank()) {
            inputs.put("vehicleVin", req.vehicleVin());
        }
        if (req.originState() != null && !req.originState().isBlank()) {
            inputs.put("originState", req.originState());
        }
        if (req.county() != null && !req.county().isBlank()) {
            inputs.put("county", req.county());
        }
        if (req.transferType() != null && !req.transferType().isBlank()) {
            inputs.put("transferType", req.transferType());
        }
        return inputs;
    }
}
