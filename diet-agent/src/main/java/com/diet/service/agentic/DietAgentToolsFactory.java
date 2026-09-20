package com.diet.service.agentic;

import com.diet.service.history.RecommendationHistoryService;
import com.diet.service.meal.MealService;
import com.diet.service.memory.UserMemoryService;
import com.diet.service.plan.MealPlanService;
import com.diet.service.shopping.ShoppingListService;
import com.diet.util.JsonService;
import org.springframework.stereotype.Component;

@Component
public class DietAgentToolsFactory {
    private final MealService meals;
    private final UserMemoryService memories;
    private final RecommendationHistoryService history;
    private final MealPlanService plans;
    private final ShoppingListService shopping;
    private final AgentMutationPolicy mutationPolicy;
    private final JsonService json;

    public DietAgentToolsFactory(MealService meals, UserMemoryService memories,
                                 RecommendationHistoryService history, MealPlanService plans,
                                 ShoppingListService shopping, AgentMutationPolicy mutationPolicy, JsonService json) {
        this.meals = meals;
        this.memories = memories;
        this.history = history;
        this.plans = plans;
        this.shopping = shopping;
        this.mutationPolicy = mutationPolicy;
        this.json = json;
    }

    public DietAgentTools create(AgentRunContext context) {
        return new DietAgentTools(context, meals, memories, history, plans, shopping, mutationPolicy, json);
    }
}
