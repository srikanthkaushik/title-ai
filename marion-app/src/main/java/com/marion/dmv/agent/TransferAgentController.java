package com.marion.dmv.agent;

import com.marion.dmv.transfer.TransferRequest;
import com.marion.dmv.transfer.TransferResponse;
import com.marion.dmv.transfer.TransferResponseParser;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphDefinition;
import org.bsc.langgraph4j.GraphInput;
import org.bsc.langgraph4j.RunnableConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/transfer")
public class TransferAgentController {

    private static final Logger log = LoggerFactory.getLogger(TransferAgentController.class);

    private static final String AWAIT_SUPERVISOR_NODE = "await_supervisor";

    private final CompiledGraph<TransferAgentState> graph;
    private final ThreadTrackingMemorySaver checkpointSaver;

    public TransferAgentController(CompiledGraph<TransferAgentState> graph,
                                    ThreadTrackingMemorySaver checkpointSaver) {
        this.graph = graph;
        this.checkpointSaver = checkpointSaver;
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
    // into graph state and routed into one more GENERATE pass, which produces the final response.
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

    // Read-only status check for a single thread. Doesn't invoke the graph — just re-reads the
    // checkpoint, same as toAgentResponse does right after invoke()/resume(). Exists so a client
    // that submitted a referral (and is showing "Awaiting Supervisor Decision") can poll for
    // completion after a *different* session resolves it via the Supervisor Queue — without this,
    // the originating tab has no way to learn the run finished and sits stuck indefinitely.
    @GetMapping(value = "/query/agent/{threadId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<AgentTransferResponse> agentStatus(@PathVariable String threadId) {
        RunnableConfig config = RunnableConfig.builder().threadId(threadId).build();
        return Mono.fromCallable(() -> toAgentResponse(config, threadId))
                .subscribeOn(Schedulers.boundedElastic());
    }

    // Server-side audit of every run currently paused at await_supervisor. MemorySaver never
    // forgets a threadId (we never call graph.release()), so this scans all threads it has ever
    // seen and filters to the ones whose state snapshot is still parked at the gate node. A single
    // unreadable thread (e.g. one whose checkpoint list is empty — can happen if a malformed/blank
    // threadId ever reaches resume() or agentStatus(), which still touches the cache via
    // computeIfAbsent) must not take down the whole audit for every other thread.
    @GetMapping(value = "/pending-referrals", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<List<PendingReferral>> pendingReferrals() {
        return Mono.fromCallable(() -> {
            List<PendingReferral> pending = new ArrayList<>();
            for (String threadId : checkpointSaver.threadIds()) {
                try {
                    RunnableConfig config = RunnableConfig.builder().threadId(threadId).build();
                    var snapshot = graph.getState(config);
                    if (!AWAIT_SUPERVISOR_NODE.equals(snapshot.next())) {
                        continue;
                    }
                    pending.add(toPendingReferral(threadId, snapshot.state()));
                } catch (Exception e) {
                    log.warn("[pendingReferrals] skipping unreadable threadId={}", threadId, e);
                }
            }
            return pending;
        })
        .subscribeOn(Schedulers.boundedElastic());
    }

    private static PendingReferral toPendingReferral(String threadId, TransferAgentState state) {
        try {
            TransferResponse response = TransferResponseParser.parse(state.draftAnswer());
            return new PendingReferral(threadId, state.question(), response.referralReason(), response.referralForm());
        } catch (IllegalArgumentException e) {
            log.warn("[pendingReferrals] threadId={} paused at await_supervisor but draftAnswer " +
                    "did not parse — should be unreachable, routing only awaits on a successful parse", threadId, e);
            return new PendingReferral(threadId, state.question(), null, null);
        }
    }

    private AgentTransferResponse toAgentResponse(RunnableConfig config, String threadId) {
        var snapshot = graph.getState(config);
        String draftAnswer = snapshot.state().draftAnswer();
        if (draftAnswer.isBlank()) {
            throw new IllegalStateException("Agent graph produced no output");
        }
        TransferResponse response = TransferResponseParser.parse(draftAnswer);
        // "Not currently AT the gate" is NOT the same as "finished" — when this is read from
        // queryAgent()/resume() right after their own synchronous invoke() call, next() can only
        // be await_supervisor (just paused) or null/END (just finished), so the two were
        // equivalent. But agentStatus() reads state from an INDEPENDENT request that can race a
        // concurrent resume() on another thread: mid-resume, the checkpoint can already show
        // next()=="generate" (past the gate, second GENERATE pass not done yet) while draftAnswer
        // is still the FIRST pass's stale answer. Treat only a truly terminal next() as "done" —
        // anything else (including that in-flight window) still counts as awaiting/incomplete.
        String next = snapshot.next();
        boolean finished = next == null || GraphDefinition.END.equals(next);
        boolean awaitingSupervisor = !finished;
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
