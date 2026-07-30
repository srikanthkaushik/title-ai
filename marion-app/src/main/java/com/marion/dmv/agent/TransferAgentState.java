package com.marion.dmv.agent;

import org.bsc.langgraph4j.state.AgentState;

import java.util.Map;
import java.util.Optional;

public class TransferAgentState extends AgentState {

    public TransferAgentState(Map<String, Object> data) {
        super(data);
    }

    public String question() {
        return this.<String>value("question").orElse("");
    }

    public Optional<String> vehicleVin() {
        return value("vehicleVin");
    }

    public Optional<String> originState() {
        return value("originState");
    }

    public Optional<String> county() {
        return value("county");
    }

    public Optional<String> transferType() {
        return value("transferType");
    }

    /** Formatted retrieved-document context from the RETRIEVE node. */
    public String context() {
        return this.<String>value("context").orElse("");
    }

    /** Formatted MCP tool results from the TOOL_FETCH node. */
    public String toolData() {
        return this.<String>value("toolData").orElse("");
    }

    /** Most recent LLM draft — replaced on each GENERATE cycle. */
    public String draftAnswer() {
        return this.<String>value("draftAnswer").orElse("");
    }

    /** Number of GENERATE cycles completed so far. */
    public int cycleCount() {
        return this.<Integer>value("cycleCount").orElse(0);
    }

    /** Parse error message from the last GENERATE cycle; empty string means parse succeeded. */
    public String parseError() {
        return this.<String>value("parseError").orElse("");
    }

    /** Whether the last successfully-parsed draft requires supervisor referral. */
    public boolean supervisorReferral() {
        return this.<Boolean>value("supervisorReferral").orElse(false);
    }

    /** Supervisor's decision ("APPROVED"/"DENIED"), populated only on resume after a referral pause. */
    public Optional<String> supervisorDecision() {
        return value("supervisorDecision");
    }

    /** Supervisor's free-text note, populated only on resume after a referral pause. */
    public Optional<String> supervisorNote() {
        return value("supervisorNote");
    }
}
