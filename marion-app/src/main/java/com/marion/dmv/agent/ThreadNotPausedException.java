package com.marion.dmv.agent;

// Thrown when resume() is called on a threadId that exists but isn't currently parked at
// await_supervisor — already resolved (e.g. a double-submitted Approve/Deny), or never paused
// in the first place. Maps to 409, distinct from the 404 "never existed" case.
public class ThreadNotPausedException extends RuntimeException {
    public ThreadNotPausedException(String threadId) {
        super("threadId " + threadId + " is not currently awaiting a supervisor decision");
    }
}
