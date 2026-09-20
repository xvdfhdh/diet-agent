package com.diet.model;

import com.diet.enums.RecommendationMode;
import java.util.List;

public record ChatExecution(
        RecommendationMode requestedMode,
        RecommendationMode actualMode,
        boolean fallbackOccurred,
        String fallbackCode,
        List<AgentActivity> activities,
        boolean mutationsCommitted
) {
    public ChatExecution {
        activities = activities == null ? List.of() : List.copyOf(activities);
    }

    public static ChatExecution standard(RecommendationMode requestedMode) {
        return new ChatExecution(requestedMode, RecommendationMode.STANDARD, false, null, List.of(), false);
    }
}
