package com.marion.dmv.agent;

// Thrown when a threadId has no checkpoint at all — never existed, or MemorySaver lost it
// across an app restart (it's in-process only). Maps to 404, distinct from a generic 500.
public class UnknownThreadException extends RuntimeException {
    public UnknownThreadException(String threadId) {
        super("No run found for threadId: " + threadId);
    }
}
