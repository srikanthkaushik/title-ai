package com.marion.dmv.agent;

/** decision must be "APPROVED" or "DENIED"; note is an optional free-text supervisor comment. */
public record SupervisorDecisionRequest(
        String threadId,
        String decision,
        String note
) {}
