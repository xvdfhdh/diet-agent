package com.diet.service.agentic;

import com.diet.model.PlanItemRequest;
import com.diet.service.plan.MealPlanService;
import com.diet.service.shopping.ShoppingListService;
import com.diet.service.favorite.FavoriteMealService;
import com.diet.service.memory.UserMemoryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AgentMutationCommitService {
    private final MealPlanService mealPlanService;
    private final ShoppingListService shoppingListService;
    private final FavoriteMealService favoriteMealService;
    private final UserMemoryService memoryService;

    public AgentMutationCommitService(MealPlanService mealPlanService, ShoppingListService shoppingListService,
                                      FavoriteMealService favoriteMealService, UserMemoryService memoryService) {
        this.mealPlanService = mealPlanService;
        this.shoppingListService = shoppingListService;
        this.favoriteMealService = favoriteMealService;
        this.memoryService = memoryService;
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
            } else if (mutation instanceof AgentMutation.CheckInPlanItem value) {
                mealPlanService.checkIn(userId, value.planId(), value.request());
            } else if (mutation instanceof AgentMutation.SetFavorite value) {
                if (value.favorite()) favoriteMealService.add(userId, value.mealId(), value.sessionId());
                else favoriteMealService.remove(userId, value.mealId());
            } else if (mutation instanceof AgentMutation.AddShoppingItem value) {
                shoppingListService.addItem(userId, value.weekStart(), value.request());
            } else if (mutation instanceof AgentMutation.UpdateShoppingItem value) {
                shoppingListService.updateItem(userId, value.itemId(), value.request());
            } else if (mutation instanceof AgentMutation.DeleteShoppingItem value) {
                shoppingListService.deleteItem(userId, value.itemId());
            } else if (mutation instanceof AgentMutation.ReplacePreferences value) {
                memoryService.replacePreferences(userId, value.request());
            }
        }
    }
}
