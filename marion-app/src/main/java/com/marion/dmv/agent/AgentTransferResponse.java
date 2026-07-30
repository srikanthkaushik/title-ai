package com.marion.dmv.agent;

import com.marion.dmv.transfer.TransferResponse;

/**
 * Wraps a TransferResponse with agent-run metadata needed for the human-in-the-loop
 * supervisor review flow. threadId identifies the paused LangGraph4j checkpoint;
 * callers must echo it back on {@code /api/transfer/query/agent/resume} when
 * awaitingSupervisorDecision is true.
 */
public record AgentTransferResponse(
        TransferResponse response,
        boolean awaitingSupervisorDecision,
        String threadId
) {}
