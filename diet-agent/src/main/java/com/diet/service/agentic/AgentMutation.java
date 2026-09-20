package com.diet.service.agentic;

import com.diet.enums.AcquisitionMode;
import com.diet.enums.MealPeriod;
import java.time.LocalDate;

public sealed interface AgentMutation permits AgentMutation.AddPlanItem, AgentMutation.ReplacePlanItem, AgentMutation.SyncShoppingList {
    record AddPlanItem(LocalDate planDate, MealPeriod mealPeriod, Long mealId,
                       AcquisitionMode acquisitionMode, Integer servings) implements AgentMutation { }
    record ReplacePlanItem(Long planId) implements AgentMutation { }
    record SyncShoppingList(LocalDate weekStart) implements AgentMutation { }
}
