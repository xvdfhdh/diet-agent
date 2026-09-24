package com.diet.service.agentic;

import com.diet.service.history.RecommendationHistoryService;
import com.diet.service.meal.MealService;
import com.diet.service.memory.UserMemoryService;
import com.diet.service.plan.MealPlanService;
import com.diet.service.shopping.ShoppingListService;
import com.diet.service.favorite.FavoriteMealService;
import com.diet.util.JsonService;
import org.springframework.stereotype.Component;
import com.diet.enums.AgentTaskType;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import io.agentscope.core.tool.Toolkit;
import java.math.BigDecimal;

@Component
public class DietAgentToolsFactory {
    private final MealService meals;
    private final UserMemoryService memories;
    private final RecommendationHistoryService history;
    private final MealPlanService plans;
    private final ShoppingListService shopping;
    private final FavoriteMealService favorites;
    private final AgentMutationPolicy mutationPolicy;
    private final JsonService json;

    public DietAgentToolsFactory(MealService meals, UserMemoryService memories,
                                 RecommendationHistoryService history, MealPlanService plans,
                                 ShoppingListService shopping, FavoriteMealService favorites,
                                 AgentMutationPolicy mutationPolicy, JsonService json) {
        this.meals = meals;
        this.memories = memories;
        this.history = history;
        this.plans = plans;
        this.shopping = shopping;
        this.favorites = favorites;
        this.mutationPolicy = mutationPolicy;
        this.json = json;
    }

    public DietAgentTools create(AgentRunContext context) {
        return new DietAgentTools(context, meals, memories, history, plans, shopping, favorites, mutationPolicy, json);
    }

    public void register(Toolkit toolkit, AgentRunContext context, AgentTaskType taskType) {
        DietAgentTools delegate = create(context);
        switch (taskType) {
            case PLAN -> toolkit.registerTool(new PlanTools(delegate));
            case SHOPPING -> toolkit.registerTool(new ShoppingTools(delegate));
            case CHECKIN -> toolkit.registerTool(new CheckinTools(delegate));
            case FAVORITE -> toolkit.registerTool(new FavoriteTools(delegate));
            case PREFERENCE -> toolkit.registerTool(new PreferenceTools(delegate));
            case RECOMMEND, GENERAL -> toolkit.registerTool(new RecommendationTools(delegate));
        }
    }

    public static final class RecommendationTools {
        private final DietAgentTools tools;
        RecommendationTools(DietAgentTools tools) { this.tools = tools; }
        @Tool(name="find_meal_options", description="跨个人库和公共库检索并排序真实餐食，返回统一工具结果。")
        public String find(@ToolParam(name="keyword", required=false) String keyword,
                           @ToolParam(name="mealTime", required=false) String mealTime,
                           @ToolParam(name="taste", required=false) String taste,
                           @ToolParam(name="cuisine", required=false) String cuisine,
                           @ToolParam(name="healthGoal", required=false) String healthGoal,
                           @ToolParam(name="acquisitionMode", required=false) String acquisitionMode,
                           @ToolParam(name="maxPrepMinutes", required=false) Integer maxPrepMinutes,
                           @ToolParam(name="maxPrice", required=false) BigDecimal maxPrice,
                           @ToolParam(name="limit", required=false) Integer limit) {
            return tools.findMealOptions(keyword, mealTime, taste, cuisine, healthGoal, acquisitionMode,
                    maxPrepMinutes, maxPrice, limit);
        }
        @Tool(name="get_meal_detail", description="读取一个本轮已检索餐食的完整详情。")
        public String detail(@ToolParam(name="mealId") Long mealId) { return tools.getMealDetail(mealId); }
        @Tool(name="get_user_diet_context", description="读取长期饮食偏好和约束。")
        public String context() { return tools.getUserDietContext(); }
        @Tool(name="get_recent_recommendations", description="读取近期推荐以避免重复。")
        public String recent(@ToolParam(name="limit", required=false) Integer limit) { return tools.getRecentRecommendations(limit); }
    }

    public static final class PlanTools {
        private final DietAgentTools tools;
        PlanTools(DietAgentTools tools) { this.tools = tools; }
        @Tool(name="find_meal_options", description="为计划跨库检索真实餐食。")
        public String find(@ToolParam(name="keyword", required=false) String keyword,
                           @ToolParam(name="mealTime", required=false) String mealTime,
                           @ToolParam(name="taste", required=false) String taste,
                           @ToolParam(name="cuisine", required=false) String cuisine,
                           @ToolParam(name="healthGoal", required=false) String healthGoal,
                           @ToolParam(name="acquisitionMode", required=false) String acquisitionMode,
                           @ToolParam(name="maxPrepMinutes", required=false) Integer maxPrepMinutes,
                           @ToolParam(name="maxPrice", required=false) BigDecimal maxPrice,
                           @ToolParam(name="limit", required=false) Integer limit) {
            return tools.findMealOptions(keyword, mealTime, taste, cuisine, healthGoal, acquisitionMode,
                    maxPrepMinutes, maxPrice, limit);
        }
        @Tool(name="get_week_plan", description="读取指定日期所在周的计划和可复用餐食 ID。")
        public String week(@ToolParam(name="date", required=false) String date) { return tools.getWeekPlan(date); }
        @Tool(name="build_today_plan", description="从最近历史日计划生成目标日期计划草案。")
        public String today(@ToolParam(name="targetDate", required=false) String targetDate,
                            @ToolParam(name="referenceDate", required=false) String referenceDate) {
            return tools.buildTodayPlan(targetDate, referenceDate);
        }
        @Tool(name="build_week_plan", description="一次生成一周三餐草案。")
        public String weekDraft(@ToolParam(name="weekStart", required=false) String weekStart,
                                @ToolParam(name="acquisitionMode", required=false) String mode) {
            return tools.buildWeekPlan(weekStart, mode);
        }
        @Tool(name="prepare_plan_changes", description="暂存一项计划新增或覆盖。")
        public String change(@ToolParam(name="planDate") String date,
                             @ToolParam(name="mealPeriod") String period,
                             @ToolParam(name="mealId") Long mealId,
                             @ToolParam(name="acquisitionMode", required=false) String mode,
                             @ToolParam(name="servings", required=false) Integer servings) {
            return tools.addOrReplacePlanItem(date, period, mealId, mode, servings);
        }
        @Tool(name="replace_plan_item", description="暂存替换一个当前用户已有的计划项。")
        public String replace(@ToolParam(name="planId") Long planId) { return tools.replacePlanItem(planId); }
    }

    public static final class ShoppingTools {
        private final DietAgentTools tools;
        ShoppingTools(DietAgentTools tools) { this.tools = tools; }
        @Tool(name="get_shopping_list", description="读取指定周购物清单。")
        public String get(@ToolParam(name="date", required=false) String date) { return tools.getShoppingList(date); }
        @Tool(name="prepare_shopping_sync", description="暂存从饮食计划同步购物清单的操作。")
        public String sync(@ToolParam(name="weekStart", required=false) String weekStart) { return tools.syncShoppingList(weekStart); }
        @Tool(name="manage_shopping_item", description="暂存购物项添加、更新、勾选或删除。")
        public String manage(@ToolParam(name="operation") String operation,
                             @ToolParam(name="itemId", required=false) Long itemId,
                             @ToolParam(name="weekStart", required=false) String weekStart,
                             @ToolParam(name="name", required=false) String name,
                             @ToolParam(name="category", required=false) String category,
                             @ToolParam(name="quantity", required=false) BigDecimal quantity,
                             @ToolParam(name="unit", required=false) String unit,
                             @ToolParam(name="completed", required=false) Boolean completed) {
            return tools.manageShoppingItem(operation, itemId, weekStart, name, category, quantity, unit, completed);
        }
    }

    public static final class CheckinTools {
        private final DietAgentTools tools;
        CheckinTools(DietAgentTools tools) { this.tools = tools; }
        @Tool(name="get_week_plan", description="读取计划项 ID 后再进行打卡。")
        public String week(@ToolParam(name="date", required=false) String date) { return tools.getWeekPlan(date); }
        @Tool(name="check_in_meal", description="记录一个计划项已吃或跳过。")
        public String checkin(@ToolParam(name="planId") Long planId,
                              @ToolParam(name="skipped", required=false) Boolean skipped,
                              @ToolParam(name="rating", required=false) Integer rating,
                              @ToolParam(name="satiety", required=false) Integer satiety,
                              @ToolParam(name="reasonCode", required=false) String reasonCode,
                              @ToolParam(name="note", required=false) String note,
                              @ToolParam(name="actualSpent", required=false) BigDecimal spent) {
            return tools.checkInMeal(planId, skipped, rating, satiety, reasonCode, note, spent);
        }
    }

    public static final class FavoriteTools {
        private final DietAgentTools tools;
        FavoriteTools(DietAgentTools tools) { this.tools = tools; }
        @Tool(name="find_meal_options", description="查找要收藏的真实餐食。")
        public String find(@ToolParam(name="keyword", required=false) String keyword,
                           @ToolParam(name="mealTime", required=false) String mealTime,
                           @ToolParam(name="taste", required=false) String taste,
                           @ToolParam(name="cuisine", required=false) String cuisine,
                           @ToolParam(name="healthGoal", required=false) String healthGoal,
                           @ToolParam(name="acquisitionMode", required=false) String acquisitionMode,
                           @ToolParam(name="maxPrepMinutes", required=false) Integer maxPrepMinutes,
                           @ToolParam(name="maxPrice", required=false) BigDecimal maxPrice,
                           @ToolParam(name="limit", required=false) Integer limit) {
            return tools.findMealOptions(keyword, mealTime, taste, cuisine, healthGoal, acquisitionMode,
                    maxPrepMinutes, maxPrice, limit);
        }
        @Tool(name="set_favorite", description="收藏或取消收藏一个真实餐食。")
        public String set(@ToolParam(name="mealId") Long mealId,
                          @ToolParam(name="favorite") Boolean favorite) { return tools.setFavorite(mealId, favorite); }
    }

    public static final class PreferenceTools {
        private final DietAgentTools tools;
        PreferenceTools(DietAgentTools tools) { this.tools = tools; }
        @Tool(name="get_user_diet_context", description="读取当前长期偏好。")
        public String get() { return tools.getUserDietContext(); }
        @Tool(name="update_diet_preference", description="增加、移除或替换明确长期偏好。")
        public String update(@ToolParam(name="operation") String operation,
                             @ToolParam(name="key") String key,
                             @ToolParam(name="value") String value) {
            return tools.updateDietPreference(operation, key, value);
        }
    }
}
