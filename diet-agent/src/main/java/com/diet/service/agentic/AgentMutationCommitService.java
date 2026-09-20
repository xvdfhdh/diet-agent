package com.diet.service.agentic;

import com.diet.model.PlanItemRequest;
import com.diet.service.plan.MealPlanService;
import com.diet.service.shopping.ShoppingListService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AgentMutationCommitService {
    private final MealPlanService mealPlanService;
    private final ShoppingListService shoppingListService;

    public AgentMutationCommitService(MealPlanService mealPlanService, ShoppingListService shoppingListService) {
        this.mealPlanService = mealPlanService;
        this.shoppingListService = shoppingListService;
    }

    @Transactional
    public void commit(Long userId, List<AgentMutation> mutations) {
        for (AgentMutation mutation : mutations == null ? List.<AgentMutation>of() : mutations) {
            if (mutation instanceof AgentMutation.AddPlanItem value) {
                mealPlanService.add(userId, new PlanItemRequest(value.planDate(), value.mealPeriod(), value.mealId(),
                        value.acquisitionMode(), value.servings()));
            } else if (mutation instanceof AgentMutation.ReplacePlanItem value) {
                mealPlanService.replace(userId, value.planId());
            } else if (mutation instanceof AgentMutation.SyncShoppingList value) {
                shoppingListService.sync(userId, value.weekStart());
            }
        }
    }
}
