package com.marion.dmv.metrics;

public record NodeMetric(
        String label,
        long count,
        double meanMs,
        double maxMs,
        double totalMs
) {}
