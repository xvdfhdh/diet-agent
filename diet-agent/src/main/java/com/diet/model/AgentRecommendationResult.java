package com.diet.model;

import java.util.List;

public record AgentRecommendationResult(
        String responseType,
        String speechText,
        List<Long> mealIds,
        SlotBundle resolvedSlots,
        String nextAction,
        String clarifyQuestion,
        List<String> missingSlots
) {
    public AgentRecommendationResult {
        mealIds = mealIds == null ? List.of() : List.copyOf(mealIds);
        resolvedSlots = resolvedSlots == null ? SlotBundle.empty() : resolvedSlots;
        missingSlots = missingSlots == null ? List.of() : List.copyOf(missingSlots);
    }
}
