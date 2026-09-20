package com.diet.service.agentic;

import com.diet.enums.AcquisitionMode;
import com.diet.enums.MealPeriod;
import com.diet.exception.DietException;
import com.diet.model.*;
import com.diet.service.history.RecommendationHistoryService;
import com.diet.service.meal.MealService;
import com.diet.service.memory.UserMemoryService;
import com.diet.service.plan.MealPlanService;
import com.diet.service.shopping.ShoppingListService;
import com.diet.util.JsonService;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class DietAgentTools {
    private final AgentRunContext context;
    private final MealService meals;
    private final UserMemoryService memories;
    private final RecommendationHistoryService history;
    private final MealPlanService plans;
    private final ShoppingListService shopping;
    private final AgentMutationPolicy mutationPolicy;
    private final JsonService json;

    DietAgentTools(AgentRunContext context, MealService meals, UserMemoryService memories,
                   RecommendationHistoryService history, MealPlanService plans,
                   ShoppingListService shopping, AgentMutationPolicy mutationPolicy, JsonService json) {
        this.context = context;
        this.meals = meals;
        this.memories = memories;
        this.history = history;
        this.plans = plans;
        this.shopping = shopping;
        this.mutationPolicy = mutationPolicy;
        this.json = json;
    }

    @Tool(name = "search_meals", description = "在用户当前选择的个人餐食库或公共餐食库中检索真实餐食。推荐前必须调用。")
    public String searchMeals(
            @ToolParam(name = "keyword", required = false, description = "名称关键词，可为空") String keyword,
            @ToolParam(name = "mealTime", required = false, description = "早餐、午餐、晚餐或加餐") String mealTime,
            @ToolParam(name = "acquisitionMode", required = false, description = "COOK、EAT_OUT 或 BOTH") String acquisitionMode,
            @ToolParam(name = "maxPrepMinutes", required = false, description = "可接受的最大准备分钟数") Integer maxPrepMinutes,
            @ToolParam(name = "maxPrice", required = false, description = "可接受的最高价格") BigDecimal maxPrice,
            @ToolParam(name = "limit", required = false, description = "返回数量，1 到 10") Integer limit) {
        context.beginTool();
        int safeLimit = Math.max(1, Math.min(limit == null ? 8 : limit, 10));
        String normalizedKeyword = clean(keyword);
        AcquisitionMode mode = parseMode(acquisitionMode);
        List<Long> disliked = memories.dislikedMealIds(context.userId());
        List<MealItem> found = meals.findAccessibleMeals(context.userId()).stream()
                .filter(meal -> meal.sourceType() == context.sourceMode())
                .filter(meal -> !disliked.contains(meal.id()))
                .filter(meal -> normalizedKeyword == null || meal.name().toLowerCase(Locale.ROOT)
                        .contains(normalizedKeyword.toLowerCase(Locale.ROOT)))
                .filter(meal -> clean(mealTime) == null || meal.slots().mealTime().contains(mealTime.trim())
                        || meal.slots().mealTime().contains("三餐"))
                .filter(meal -> mode == null || meal.detail().acquisitionMode() == AcquisitionMode.BOTH
                        || meal.detail().acquisitionMode() == mode)
                .filter(meal -> maxPrepMinutes == null || meal.detail().prepMinutes() == null
                        || meal.detail().prepMinutes() <= maxPrepMinutes)
                .filter(meal -> maxPrice == null || meal.detail().priceMin() == null
                        || meal.detail().priceMin().compareTo(maxPrice) <= 0)
                .limit(safeLimit).toList();
        context.rememberMeals(found.stream().map(MealItem::id).toList());
        context.activity(AgentActivity.completed("检索餐食", "找到 " + found.size() + " 道候选"));
        return json.toJson(found.stream().map(MealResponse::from).toList());
    }

    @Tool(name = "get_meal_detail", description = "查看一个已检索餐食的完整详情。")
    public String getMealDetail(@ToolParam(name = "mealId", description = "餐食 ID") Long mealId) {
        context.beginTool();
        if (!context.wasRetrieved(mealId)) throw new DietException("只能查看本轮检索结果中的餐食");
        MealItem meal = meals.findAccessibleMeal(context.userId(), mealId);
        if (meal == null || meal.sourceType() != context.sourceMode()) throw new DietException("餐食不存在或无权访问");
        context.activity(AgentActivity.completed("查看餐食详情", meal.name()));
        return json.toJson(MealResponse.from(meal));
    }

    @Tool(name = "get_user_diet_context", description = "读取当前用户的长期饮食偏好、行为约束和不喜欢的餐食 ID。")
    public String getUserDietContext() {
        context.beginTool();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("preferences", memories.preferences(context.userId()));
        result.put("constraints", memories.behaviorConstraints(context.userId()));
        result.put("dislikedMealIds", memories.dislikedMealIds(context.userId()));
        context.activity(AgentActivity.completed("读取长期偏好", "已应用个人偏好与约束"));
        return json.toJson(result);
    }

    @Tool(name = "get_recent_recommendations", description = "读取当前用户最近的推荐记录，帮助避免重复。")
    public String getRecentRecommendations(@ToolParam(name = "limit", required = false, description = "1 到 5 条") Integer limit) {
        context.beginTool();
        int safeLimit = Math.max(1, Math.min(limit == null ? 3 : limit, 5));
        List<RecommendationHistoryResponse> values = history.recent(context.userId(), 10).stream()
                .filter(item -> item.sourceMode() == context.sourceMode()).limit(safeLimit).toList();
        context.activity(AgentActivity.completed("查看近期推荐", "读取 " + values.size() + " 条记录"));
        return json.toJson(values);
    }

    @Tool(name = "get_week_plan", description = "查看当前用户指定日期所在周的饮食计划。")
    public String getWeekPlan(@ToolParam(name = "date", required = false, description = "ISO 日期 yyyy-MM-dd，默认今天") String date) {
        context.beginTool();
        LocalDate value = parseDate(date, LocalDate.now());
        List<PlanItemResponse> result = plans.findWeek(context.userId(), value);
        context.activity(AgentActivity.completed("查看本周计划", "已有 " + result.size() + " 个安排"));
        return json.toJson(result);
    }

    @Tool(name = "get_shopping_list", description = "查看当前用户指定日期所在周的购物清单。")
    public String getShoppingList(@ToolParam(name = "date", required = false, description = "ISO 日期 yyyy-MM-dd，默认今天") String date) {
        context.beginTool();
        ShoppingListResponse result = shopping.get(context.userId(), parseDate(date, LocalDate.now()));
        context.activity(AgentActivity.completed("查看购物清单", "共有 " + result.items().size() + " 项"));
        return json.toJson(result);
    }

    @Tool(name = "add_or_replace_plan_item", description = "暂存把已检索餐食加入指定日期和餐次的操作；同一位置已有餐食时会替换。")
    public String addOrReplacePlanItem(
            @ToolParam(name = "planDate", description = "ISO 日期 yyyy-MM-dd") String planDate,
            @ToolParam(name = "mealPeriod", description = "BREAKFAST、LUNCH、DINNER 或 SNACK") String mealPeriod,
            @ToolParam(name = "mealId", description = "本轮已检索的餐食 ID") Long mealId,
            @ToolParam(name = "acquisitionMode", required = false, description = "COOK 或 EAT_OUT") String acquisitionMode,
            @ToolParam(name = "servings", required = false, description = "份数 1 到 20") Integer servings) {
        context.beginTool();
        if (!mutationPolicy.mayAddPlan(context.userInput())) throw new DietException("用户没有明确要求修改饮食计划");
        if (!context.wasRetrieved(mealId)) throw new DietException("只能把本轮检索到的餐食加入计划");
        LocalDate date = parseDate(planDate, null);
        MealPeriod period = MealPeriod.valueOf(required(mealPeriod).toUpperCase(Locale.ROOT));
        AcquisitionMode mode = parseMode(acquisitionMode);
        int safeServings = servings == null ? 1 : servings;
        if (safeServings < 1 || safeServings > 20) throw new DietException("份数需在 1~20 之间");
        context.stage(new AgentMutation.AddPlanItem(date, period, mealId, mode, safeServings));
        context.activity(AgentActivity.staged("准备加入计划", date + " · " + period.label()));
        return json.toJson(Map.of("staged", true, "planDate", date, "mealPeriod", period, "mealId", mealId));
    }

    @Tool(name = "replace_plan_item", description = "暂存把一个已有计划项替换为相似餐食的操作。")
    public String replacePlanItem(@ToolParam(name = "planId", description = "当前用户的计划项 ID") Long planId) {
        context.beginTool();
        if (!mutationPolicy.mayReplacePlan(context.userInput())) throw new DietException("用户没有明确要求替换计划餐食");
        PlanItemResponse item = plans.findOwned(context.userId(), planId);
        context.stage(new AgentMutation.ReplacePlanItem(planId));
        context.activity(AgentActivity.staged("准备替换计划餐食", item.planDate() + " · " + item.mealPeriod().label()));
        return json.toJson(Map.of("staged", true, "planId", planId));
    }

    @Tool(name = "sync_shopping_list", description = "暂存根据指定周饮食计划同步购物清单的操作。")
    public String syncShoppingList(@ToolParam(name = "weekStart", required = false, description = "周内任意 ISO 日期，默认本周") String weekStart) {
        context.beginTool();
        if (!mutationPolicy.maySyncShopping(context.userInput())) throw new DietException("用户没有明确要求同步购物清单");
        LocalDate date = parseDate(weekStart, LocalDate.now());
        context.stage(new AgentMutation.SyncShoppingList(date));
        context.activity(AgentActivity.staged("准备同步购物清单", plans.weekStart(date).toString()));
        return json.toJson(Map.of("staged", true, "weekStart", plans.weekStart(date)));
    }

    private AcquisitionMode parseMode(String value) {
        String cleaned = clean(value);
        if (cleaned == null || "BOTH".equalsIgnoreCase(cleaned)) return null;
        try { return AcquisitionMode.valueOf(cleaned.toUpperCase(Locale.ROOT)); }
        catch (Exception error) { throw new DietException("获取方式必须是 COOK、EAT_OUT 或 BOTH"); }
    }

    private LocalDate parseDate(String value, LocalDate fallback) {
        if (clean(value) == null) {
            if (fallback != null) return fallback;
            throw new DietException("日期不能为空");
        }
        try {
            LocalDate result = LocalDate.parse(value.trim());
            if (result.isBefore(LocalDate.now().minusYears(1)) || result.isAfter(LocalDate.now().plusYears(1)))
                throw new DietException("日期超出允许范围");
            return result;
        } catch (DietException error) { throw error; }
        catch (Exception error) { throw new DietException("日期格式必须是 yyyy-MM-dd"); }
    }

    private String clean(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private String required(String value) { String result = clean(value); if (result == null) throw new DietException("参数不能为空"); return result; }
}
