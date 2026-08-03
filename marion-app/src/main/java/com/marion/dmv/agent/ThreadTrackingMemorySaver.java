package com.marion.dmv.agent;

import org.bsc.langgraph4j.checkpoint.MemorySaver;

import java.util.Set;

/**
 * MemorySaver.cache() is protected, not public — there's no supported way to list every
 * threadId it holds. Subclassing exposes it: protected members are visible to a subclass
 * even in a different package, just not through a superclass-typed reference.
 */
public class ThreadTrackingMemorySaver extends MemorySaver {

    public Set<String> threadIds() {
        return cache().keySet();
    }
}
