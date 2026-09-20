package com.diet.service.agentic;

import com.diet.model.AgentActivity;
import java.util.List;

public class AgentRunException extends RuntimeException {
    private final String code;
    private final List<AgentActivity> activities;
    private final int toolCallCount;
    private final boolean mutationRequested;

    public AgentRunException(String code, String message, Throwable cause, List<AgentActivity> activities,
                             int toolCallCount, boolean mutationRequested) {
        super(message, cause);
        this.code = code;
        this.activities = activities == null ? List.of() : List.copyOf(activities);
        this.toolCallCount = toolCallCount;
        this.mutationRequested = mutationRequested;
    }

    public String code() { return code; }
    public List<AgentActivity> activities() { return activities; }
    public int toolCallCount() { return toolCallCount; }
    public boolean mutationRequested() { return mutationRequested; }
}
