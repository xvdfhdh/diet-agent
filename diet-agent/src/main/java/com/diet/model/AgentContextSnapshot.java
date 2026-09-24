package com.diet.model;

import com.diet.enums.AgentTaskType;
import com.diet.enums.SourceStrategy;
import java.util.List;
import java.util.Set;

public record AgentContextSnapshot(
        AgentTaskType taskType,
        SourceStrategy sourceStrategy,
        UserPreferenceProfile preferences,
        Set<String> constraints,
        List<Long> dislikedMealIds,
        List<RecommendationHistoryResponse> recentRecommendations,
        List<PlanItemResponse> weekPlan,
        ShoppingListResponse shoppingList,
        String conversationSummary,
        List<ConversationTurn> recentConversation
) {
    public AgentContextSnapshot {
        constraints = constraints == null ? Set.of() : Set.copyOf(constraints);
        dislikedMealIds = dislikedMealIds == null ? List.of() : List.copyOf(dislikedMealIds);
        recentRecommendations = recentRecommendations == null ? List.of() : List.copyOf(recentRecommendations);
        weekPlan = weekPlan == null ? List.of() : List.copyOf(weekPlan);
        recentConversation = recentConversation == null ? List.of() : List.copyOf(recentConversation);
    }
}
