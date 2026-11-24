package com.iam.assistant.agent.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/** Per-query bookkeeping shared by the tool callbacks: which tools ran, and the call budget. */
public class ToolRun {

    private final int maxCalls;
    private final AtomicInteger calls = new AtomicInteger();
    private final List<String> toolsUsed = Collections.synchronizedList(new ArrayList<>());

    public ToolRun(int maxCalls) {
        this.maxCalls = maxCalls;
    }

    /** @return false once the budget is spent (the call must then not be executed) */
    public boolean tryAcquire(String toolName) {
        if (calls.incrementAndGet() > maxCalls) {
            return false;
        }
        toolsUsed.add(toolName);
        return true;
    }

    public List<String> getToolsUsed() {
        synchronized (toolsUsed) {
            return new ArrayList<>(toolsUsed);
        }
    }
}
