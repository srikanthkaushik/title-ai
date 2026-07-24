package com.marion.dmv.agent;

import com.marion.dmv.transfer.TransferRequest;
import org.bsc.langgraph4j.CompiledGraph;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/transfer")
public class TransferAgentController {

    private final CompiledGraph<TransferAgentState> graph;

    public TransferAgentController(CompiledGraph<TransferAgentState> graph) {
        this.graph = graph;
    }

    @PostMapping(value = "/query/agent", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> queryAgent(@RequestBody TransferRequest request) {
        Map<String, Object> inputs = buildInputs(request);

        return Mono.fromCallable(() -> {
            var result = graph.invoke(inputs);
            return result
                    .map(TransferAgentState::draftAnswer)
                    .filter(s -> !s.isBlank())
                    .orElse("{\"error\":\"Agent graph produced no output\"}");
        })
        .subscribeOn(Schedulers.boundedElastic())
        .flatMapMany(text -> Flux.just(text, "[DONE]"));
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
