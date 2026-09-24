package com.diet.model;

import com.diet.enums.RecommendationMode;
import com.diet.enums.AgentTaskType;
import com.diet.enums.SourceStrategy;
import java.util.List;

public record ChatExecution(
        RecommendationMode requestedMode,
        RecommendationMode actualMode,
        boolean fallbackOccurred,
        String fallbackCode,
        List<AgentActivity> activities,
        boolean mutationsCommitted,
        AgentTaskType taskType,
        SourceStrategy sourceStrategy,
        int repairCount
) {
    public ChatExecution {
        activities = activities == null ? List.of() : List.copyOf(activities);
    }

    public static ChatExecution standard(RecommendationMode requestedMode) {
        return new ChatExecution(requestedMode, RecommendationMode.STANDARD, false, null, List.of(), false,
                null, SourceStrategy.SELECTED_ONLY, 0);
    }
}
