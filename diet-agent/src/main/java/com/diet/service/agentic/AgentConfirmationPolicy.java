package com.diet.service.agentic;

import com.diet.model.AgentActionChange;
import com.diet.model.PlanItemResponse;
import com.diet.service.plan.MealPlanService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class AgentConfirmationPolicy {
    private final MealPlanService plans;

    public AgentConfirmationPolicy(MealPlanService plans) { this.plans = plans; }

    public boolean requiresConfirmation(Long userId, List<AgentMutation> mutations) {
        if (mutations == null || mutations.isEmpty()) return false;
        if (mutations.size() >= 2) return true;
        AgentMutation value = mutations.get(0);
        if (value instanceof AgentMutation.SyncShoppingList
                || value instanceof AgentMutation.DeleteShoppingItem) return true;
        if (value instanceof AgentMutation.ReplacePreferences preferences) return preferences.destructive();
        if (value instanceof AgentMutation.AddPlanItem add) {
            return plans.findWeek(userId, add.planDate()).stream()
                    .anyMatch(item -> item.planDate().equals(add.planDate()) && item.mealPeriod() == add.mealPeriod());
        }
        return false;
    }

    public List<AgentActionChange> describe(List<AgentMutation> mutations) {
        List<AgentActionChange> values = new ArrayList<>();
        for (AgentMutation value : mutations == null ? List.<AgentMutation>of() : mutations) {
            if (value instanceof AgentMutation.AddPlanItem add)
                values.add(new AgentActionChange("PLAN", "UPSERT", add.planDate() + " · " + add.mealPeriod().label() + " 加入餐食 #" + add.mealId()));
            else if (value instanceof AgentMutation.ReplacePlanItem replace)
                values.add(new AgentActionChange("PLAN", "REPLACE", "替换计划项 #" + replace.planId()));
            else if (value instanceof AgentMutation.SyncShoppingList sync)
                values.add(new AgentActionChange("SHOPPING", "SYNC", "同步 " + sync.weekStart() + " 所在周购物清单"));
            else if (value instanceof AgentMutation.CheckInPlanItem checkin)
                values.add(new AgentActionChange("CHECKIN", "CHECK_IN", "记录计划项 #" + checkin.planId()));
            else if (value instanceof AgentMutation.SetFavorite favorite)
                values.add(new AgentActionChange("FAVORITE", favorite.favorite() ? "ADD" : "REMOVE", "餐食 #" + favorite.mealId()));
            else if (value instanceof AgentMutation.AddShoppingItem add)
                values.add(new AgentActionChange("SHOPPING", "ADD", "添加购物项 " + add.request().name()));
            else if (value instanceof AgentMutation.UpdateShoppingItem update)
                values.add(new AgentActionChange("SHOPPING", "UPDATE", "更新购物项 #" + update.itemId()));
            else if (value instanceof AgentMutation.DeleteShoppingItem delete)
                values.add(new AgentActionChange("SHOPPING", "DELETE", "删除购物项 #" + delete.itemId()));
            else if (value instanceof AgentMutation.ReplacePreferences)
                values.add(new AgentActionChange("PREFERENCE", "REPLACE", "更新长期饮食偏好"));
        }
        return List.copyOf(values);
    }

    public String summary(List<AgentMutation> mutations) {
        int size = mutations == null ? 0 : mutations.size();
        if (size == 1 && mutations.get(0) instanceof AgentMutation.SyncShoppingList) return "同步本周购物清单";
        if (size == 1 && mutations.get(0) instanceof AgentMutation.ReplacePreferences) return "更新长期饮食偏好";
        return "准备执行 " + size + " 项饮食助手操作";
    }
}
