package com.diet.service.agentic;

import com.diet.enums.AgentTaskType;
import com.diet.enums.SourceStrategy;
import com.diet.model.AgentContextSnapshot;
import com.diet.model.ShoppingListResponse;
import com.diet.service.history.RecommendationHistoryService;
import com.diet.service.memory.UserMemoryService;
import com.diet.service.plan.MealPlanService;
import com.diet.service.session.SessionService;
import com.diet.service.shopping.ShoppingListService;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
public class AgentContextAssembler {
    private final UserMemoryService memories;
    private final RecommendationHistoryService history;
    private final MealPlanService plans;
    private final ShoppingListService shopping;
    private final SessionService sessions;

    public AgentContextAssembler(UserMemoryService memories, RecommendationHistoryService history,
                                 MealPlanService plans, ShoppingListService shopping, SessionService sessions) {
        this.memories = memories; this.history = history; this.plans = plans;
        this.shopping = shopping; this.sessions = sessions;
    }

    public AgentContextSnapshot assemble(Long userId, String sessionId, AgentTaskType taskType,
                                         SourceStrategy sourceStrategy) {
        LocalDate today = LocalDate.now();
        ShoppingListResponse shoppingList = taskType == AgentTaskType.PLAN || taskType == AgentTaskType.SHOPPING
                ? shopping.get(userId, today) : null;
        return new AgentContextSnapshot(taskType, sourceStrategy, memories.preferences(userId),
                memories.behaviorConstraints(userId), memories.dislikedMealIds(userId), history.recent(userId, 5),
                plans.findWeek(userId, today), shoppingList,
                sessions.contextSummary(sessionId, userId),
                sessions.recentConversationTurns(sessionId, userId, 8));
    }
}
