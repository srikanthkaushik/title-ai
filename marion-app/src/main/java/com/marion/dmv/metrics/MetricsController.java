package com.marion.dmv.metrics;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

// Thin, UI-friendly view over the marion.* Micrometer timers already registered by
// TransferAgentGraph (per-node latency), RetrievalService, and RerankingService — reads
// whatever's currently in the MeterRegistry rather than hardcoding tag combinations, so a new
// timer with a "marion." prefix shows up here automatically.
@RestController
@RequestMapping("/api/metrics")
public class MetricsController {

    private final MeterRegistry meterRegistry;

    public MetricsController(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @GetMapping(value = "/summary", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<NodeMetric> summary() {
        return meterRegistry.getMeters().stream()
                .filter(m -> m.getId().getName().startsWith("marion."))
                .filter(m -> m instanceof Timer)
                .map(m -> toNodeMetric((Timer) m))
                .sorted(Comparator.comparing(NodeMetric::label))
                .toList();
    }

    private static NodeMetric toNodeMetric(Timer timer) {
        return new NodeMetric(
                label(timer),
                timer.count(),
                round(timer.mean(TimeUnit.MILLISECONDS)),
                round(timer.max(TimeUnit.MILLISECONDS)),
                round(timer.totalTime(TimeUnit.MILLISECONDS))
        );
    }

    private static String label(Timer timer) {
        Meter.Id id = timer.getId();
        String tags = id.getTags().stream()
                .map(Tag::getValue)
                .collect(Collectors.joining(", "));
        return tags.isBlank() ? id.getName() : id.getName() + " (" + tags + ")";
    }

    private static double round(double ms) {
        return Math.round(ms * 100.0) / 100.0;
    }
}
