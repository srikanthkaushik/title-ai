package com.marion.dmv.retrieval;

public record RetrievalResult(
        String text,
        String source,
        String sourceType,
        double score
) {}
