package com.diet.service.agentic;

import com.diet.enums.AcquisitionMode;
import com.diet.enums.MealPeriod;
import com.diet.model.MealCheckinRequest;
import com.diet.model.ShoppingItemRequest;
import com.diet.model.UserPreferenceRequest;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.time.LocalDate;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = AgentMutation.AddPlanItem.class, name = "ADD_PLAN_ITEM"),
        @JsonSubTypes.Type(value = AgentMutation.ReplacePlanItem.class, name = "REPLACE_PLAN_ITEM"),
        @JsonSubTypes.Type(value = AgentMutation.SyncShoppingList.class, name = "SYNC_SHOPPING_LIST"),
        @JsonSubTypes.Type(value = AgentMutation.CheckInPlanItem.class, name = "CHECK_IN_PLAN_ITEM"),
        @JsonSubTypes.Type(value = AgentMutation.SetFavorite.class, name = "SET_FAVORITE"),
        @JsonSubTypes.Type(value = AgentMutation.AddShoppingItem.class, name = "ADD_SHOPPING_ITEM"),
        @JsonSubTypes.Type(value = AgentMutation.UpdateShoppingItem.class, name = "UPDATE_SHOPPING_ITEM"),
        @JsonSubTypes.Type(value = AgentMutation.DeleteShoppingItem.class, name = "DELETE_SHOPPING_ITEM"),
        @JsonSubTypes.Type(value = AgentMutation.ReplacePreferences.class, name = "REPLACE_PREFERENCES")
})
public sealed interface AgentMutation permits AgentMutation.AddPlanItem, AgentMutation.ReplacePlanItem,
        AgentMutation.SyncShoppingList, AgentMutation.CheckInPlanItem, AgentMutation.SetFavorite,
        AgentMutation.AddShoppingItem, AgentMutation.UpdateShoppingItem, AgentMutation.DeleteShoppingItem,
        AgentMutation.ReplacePreferences {
    record AddPlanItem(LocalDate planDate, MealPeriod mealPeriod, Long mealId,
                       AcquisitionMode acquisitionMode, Integer servings) implements AgentMutation { }
    record ReplacePlanItem(Long planId) implements AgentMutation { }
    record SyncShoppingList(LocalDate weekStart) implements AgentMutation { }
    record CheckInPlanItem(Long planId, MealCheckinRequest request) implements AgentMutation { }
    record SetFavorite(Long mealId, boolean favorite, String sessionId) implements AgentMutation { }
    record AddShoppingItem(LocalDate weekStart, ShoppingItemRequest request) implements AgentMutation { }
    record UpdateShoppingItem(Long itemId, ShoppingItemRequest request) implements AgentMutation { }
    record DeleteShoppingItem(Long itemId) implements AgentMutation { }
    record ReplacePreferences(UserPreferenceRequest request, boolean destructive) implements AgentMutation { }
}
