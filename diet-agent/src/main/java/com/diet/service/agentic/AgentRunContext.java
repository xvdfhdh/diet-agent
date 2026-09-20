package com.diet.service.agentic;

import com.diet.enums.SourceMode;
import com.diet.exception.DietException;
import com.diet.model.AgentActivity;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

public class AgentRunContext {
    private static final int MAX_TOOL_CALLS = 8;
    private final Long userId;
    private final SourceMode sourceMode;
    private final String userInput;
    private final Consumer<AgentActivity> activityConsumer;
    private final List<AgentActivity> activities = new ArrayList<>();
    private final Set<Long> retrievedMealIds = new LinkedHashSet<>();
    private final List<AgentMutation> stagedMutations = new ArrayList<>();
    private int toolCallCount;
    private boolean limitExceeded;

    public AgentRunContext(Long userId, SourceMode sourceMode, String userInput, Consumer<AgentActivity> activityConsumer) {
        this.userId = userId;
        this.sourceMode = sourceMode;
        this.userInput = userInput;
        this.activityConsumer = activityConsumer == null ? ignored -> { } : activityConsumer;
    }

    public synchronized void beginTool() {
        toolCallCount++;
        if (toolCallCount > MAX_TOOL_CALLS) {
            limitExceeded = true;
            throw new DietException("智能推荐工具调用次数超过限制");
        }
    }

    public synchronized void activity(AgentActivity activity) {
        activities.add(activity);
        activityConsumer.accept(activity);
    }

    public synchronized void rememberMeals(List<Long> ids) {
        if (ids != null) retrievedMealIds.addAll(ids);
    }

    public synchronized boolean wasRetrieved(Long mealId) { return mealId != null && retrievedMealIds.contains(mealId); }
    public synchronized void stage(AgentMutation mutation) { stagedMutations.add(mutation); }
    public Long userId() { return userId; }
    public SourceMode sourceMode() { return sourceMode; }
    public String userInput() { return userInput; }
    public synchronized List<AgentActivity> activities() { return List.copyOf(activities); }
    public synchronized List<AgentMutation> stagedMutations() { return List.copyOf(stagedMutations); }
    public synchronized int toolCallCount() { return toolCallCount; }
    public synchronized boolean limitExceeded() { return limitExceeded; }
}
