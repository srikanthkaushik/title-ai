package com.marion.dmv.transfer;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

public final class TransferResponseParser {

    private static final JsonMapper MAPPER = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    private TransferResponseParser() {}

    /**
     * Extracts the first JSON object from raw LLM output, strips qwen2.5:7b comment quirks,
     * and deserializes into TransferResponse. Throws IllegalArgumentException on any failure.
     */
    public static TransferResponse parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Empty response from model");
        }
        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new IllegalArgumentException(
                    "No JSON object found — response must start with { and end with }");
        }
        String json = raw.substring(start, end + 1);
        json = json.replaceAll("(?s)/\\*.*?\\*/", "");
        json = json.replaceAll("//[^\n]*", "");
        try {
            return MAPPER.readValue(json, TransferResponse.class);
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "JSON deserialization failed: " + e.getMessage(), e);
        }
    }
}
