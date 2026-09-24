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
import com.diet.service.favorite.FavoriteMealService;
import com.diet.util.JsonService;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.LinkedHashSet;
import java.util.ArrayList;
import java.util.Comparator;

public class DietAgentTools {
    private final AgentRunContext context;
    private final MealService meals;
    private final UserMemoryService memories;
    private final RecommendationHistoryService history;
    private final MealPlanService plans;
    private final ShoppingListService shopping;
    private final FavoriteMealService favorites;
    private final AgentMutationPolicy mutationPolicy;
    private final JsonService json;

    DietAgentTools(AgentRunContext context, MealService meals, UserMemoryService memories,
                   RecommendationHistoryService history, MealPlanService plans,
                   ShoppingListService shopping, AgentMutationPolicy mutationPolicy, JsonService json) {
        this(context, meals, memories, history, plans, shopping, null, mutationPolicy, json);
    }

    DietAgentTools(AgentRunContext context, MealService meals, UserMemoryService memories,
                   RecommendationHistoryService history, MealPlanService plans,
                   ShoppingListService shopping, FavoriteMealService favorites,
                   AgentMutationPolicy mutationPolicy, JsonService json) {
        this.context = context;
        this.meals = meals;
        this.memories = memories;
        this.history = history;
        this.plans = plans;
        this.shopping = shopping;
        this.favorites = favorites;
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
                .filter(this::sourceAllowed)
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

    @Tool(name = "find_meal_options", description = "跨个人库和公共库检索并排序真实餐食；智能模式优先个人餐食，并应用长期偏好和近期去重。")
    public String findMealOptions(
            @ToolParam(name = "keyword", required = false) String keyword,
            @ToolParam(name = "mealTime", required = false, description = "早餐、午餐、晚餐或加餐") String mealTime,
            @ToolParam(name = "taste", required = false, description = "口味标签") String taste,
            @ToolParam(name = "cuisine", required = false, description = "菜系标签") String cuisine,
            @ToolParam(name = "healthGoal", required = false, description = "健康目标标签") String healthGoal,
            @ToolParam(name = "acquisitionMode", required = false, description = "COOK、EAT_OUT 或 BOTH") String acquisitionMode,
            @ToolParam(name = "maxPrepMinutes", required = false) Integer maxPrepMinutes,
            @ToolParam(name = "maxPrice", required = false) BigDecimal maxPrice,
            @ToolParam(name = "limit", required = false) Integer limit) {
        context.beginTool();
        try {
            int safeLimit = Math.max(1, Math.min(limit == null ? 8 : limit, 10));
            AcquisitionMode mode = parseMode(acquisitionMode);
            Set<Long> disliked = new LinkedHashSet<>(memories.dislikedMealIds(context.userId()));
            Set<Long> recentIds = new LinkedHashSet<>();
            history.recent(context.userId(), 10).forEach(item -> item.meals().forEach(meal -> recentIds.add(meal.id())));
            Set<Long> favoriteIds = new LinkedHashSet<>();
            if (favorites != null) favorites.list(context.userId(), 100).forEach(item -> favoriteIds.add(item.meal().id()));
            UserPreferenceProfile preferences = memories.preferences(context.userId());
            List<MealItem> found = meals.findAccessibleMeals(context.userId()).stream()
                    .filter(this::sourceAllowed)
                    .filter(meal -> !disliked.contains(meal.id()))
                    .filter(meal -> matches(meal.name(), keyword))
                    .filter(meal -> tagMatches(meal.slots().mealTime(), mealTime, "三餐"))
                    .filter(meal -> mode == null || meal.detail().acquisitionMode() == AcquisitionMode.BOTH
                            || meal.detail().acquisitionMode() == mode)
                    .filter(meal -> maxPrepMinutes == null || meal.detail().prepMinutes() == null
                            || meal.detail().prepMinutes() <= maxPrepMinutes)
                    .filter(meal -> maxPrice == null || meal.detail().priceMin() == null
                            || meal.detail().priceMin().compareTo(maxPrice) <= 0)
                    .peek(meal -> meal.matchScore(score(meal, taste, cuisine, healthGoal,
                            preferences, favoriteIds, recentIds)))
                    .sorted(Comparator.comparingDouble((MealItem meal) -> meal.matchScore()).reversed()
                            .thenComparing(meal -> meal.id()))
                    .limit(safeLimit).toList();
            context.rememberMeals(found.stream().map(MealItem::id).toList());
            context.activity(AgentActivity.completed("智能跨库检索", "找到 " + found.size() + " 道候选，个人餐食优先"));
            return json.toJson(AgentToolResult.ok(found.stream().map(MealResponse::from).toList(), "检索完成"));
        } catch (DietException error) {
            return json.toJson(AgentToolResult.retryable("INVALID_ARGUMENT", error.getMessage()));
        }
    }

    @Tool(name = "get_meal_detail", description = "查看一个已检索餐食的完整详情。")
    public String getMealDetail(@ToolParam(name = "mealId", description = "餐食 ID") Long mealId) {
        context.beginTool();
        if (!context.wasRetrieved(mealId)) throw new DietException("只能查看本轮检索结果中的餐食");
        MealItem meal = meals.findAccessibleMeal(context.userId(), mealId);
        if (meal == null || !sourceAllowed(meal)) throw new DietException("餐食不存在或无权访问");
        context.activity(AgentActivity.completed("查看餐食详情", meal.name()));
        return json.toJson(AgentToolResult.ok(MealResponse.from(meal), "已读取餐食详情"));
    }

    @Tool(name = "get_user_diet_context", description = "读取当前用户的长期饮食偏好、行为约束和不喜欢的餐食 ID。")
    public String getUserDietContext() {
        context.beginTool();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("preferences", memories.preferences(context.userId()));
        result.put("constraints", memories.behaviorConstraints(context.userId()));
        result.put("dislikedMealIds", memories.dislikedMealIds(context.userId()));
        context.activity(AgentActivity.completed("读取长期偏好", "已应用个人偏好与约束"));
        return json.toJson(AgentToolResult.ok(result, "已读取长期饮食上下文"));
    }

    @Tool(name = "get_recent_recommendations", description = "读取当前用户最近的推荐记录，帮助避免重复。")
    public String getRecentRecommendations(@ToolParam(name = "limit", required = false, description = "1 到 5 条") Integer limit) {
        context.beginTool();
        int safeLimit = Math.max(1, Math.min(limit == null ? 3 : limit, 5));
        List<RecommendationHistoryResponse> values = history.recent(context.userId(), 10).stream()
                .filter(item -> item.sourceMode() == context.sourceMode()).limit(safeLimit).toList();
        context.activity(AgentActivity.completed("查看近期推荐", "读取 " + values.size() + " 条记录"));
        return json.toJson(AgentToolResult.ok(values, "已读取近期推荐"));
    }

    @Tool(name = "get_week_plan", description = "查看当前用户指定日期所在周的饮食计划。返回当前餐食库中可安全复用的餐食 ID；按历史计划填写新计划时应优先调用。")
    public String getWeekPlan(@ToolParam(name = "date", required = false, description = "ISO 日期 yyyy-MM-dd，默认今天") String date) {
        context.beginTool();
        LocalDate value = parseDate(date, LocalDate.now());
        List<PlanItemResponse> result = plans.findWeek(context.userId(), value);
        List<Long> reusableMealIds = result.stream()
                .filter(item -> item.mealId() != null && item.meal() != null
                        && sourceAllowedResponse(item.meal()))
                .map(PlanItemResponse::mealId)
                .distinct()
                .filter(this::isAccessible)
                .toList();
        // 计划项来自当前用户的数据库记录；再次校验餐食仍可访问且属于当前数据源后，
        // 将其视为与 search_meals 相同级别的可信 ID，允许精确复用历史计划。
        context.rememberMeals(reusableMealIds);
        context.activity(AgentActivity.completed("查看本周计划",
                "已有 " + result.size() + " 个安排，当前餐食库可复用 " + reusableMealIds.size() + " 道"));
        return json.toJson(AgentToolResult.ok(Map.of(
                "currentSource", context.sourceMode(),
                "items", result,
                "reusableMealIds", reusableMealIds), "已读取本周计划"));
    }

    @Tool(name = "get_shopping_list", description = "查看当前用户指定日期所在周的购物清单。")
    public String getShoppingList(@ToolParam(name = "date", required = false, description = "ISO 日期 yyyy-MM-dd，默认今天") String date) {
        context.beginTool();
        ShoppingListResponse result = shopping.get(context.userId(), parseDate(date, LocalDate.now()));
        context.activity(AgentActivity.completed("查看购物清单", "共有 " + result.items().size() + " 项"));
        return json.toJson(AgentToolResult.ok(result, "已读取购物清单"));
    }

    @Tool(name = "add_or_replace_plan_item", description = "暂存把 search_meals 检索到、或 get_week_plan 返回为 reusableMealIds 的餐食加入指定日期和餐次；同一位置已有餐食时会替换。")
    public String addOrReplacePlanItem(
            @ToolParam(name = "planDate", description = "ISO 日期 yyyy-MM-dd") String planDate,
            @ToolParam(name = "mealPeriod", description = "BREAKFAST、LUNCH、DINNER 或 SNACK") String mealPeriod,
            @ToolParam(name = "mealId", description = "本轮已检索的餐食 ID") Long mealId,
            @ToolParam(name = "acquisitionMode", required = false, description = "COOK 或 EAT_OUT") String acquisitionMode,
            @ToolParam(name = "servings", required = false, description = "份数 1 到 20") Integer servings) {
        context.beginTool();
        if (!mutationPolicy.mayAddPlan(context.userInput())) throw new DietException("用户没有明确要求修改饮食计划");
        if (!context.wasRetrieved(mealId)) throw new DietException("只能把本轮检索到或从历史计划验证过的餐食加入计划");
        LocalDate date = parseDate(planDate, null);
        MealPeriod period = MealPeriod.valueOf(required(mealPeriod).toUpperCase(Locale.ROOT));
        AcquisitionMode mode = parseMode(acquisitionMode);
        int safeServings = servings == null ? 1 : servings;
        if (safeServings < 1 || safeServings > 20) throw new DietException("份数需在 1~20 之间");
        context.stage(new AgentMutation.AddPlanItem(date, period, mealId, mode, safeServings));
        context.activity(AgentActivity.staged("准备加入计划", date + " · " + period.label()));
        return json.toJson(AgentToolResult.ok(Map.of("staged", true, "planDate", date, "mealPeriod", period, "mealId", mealId), "计划变更已暂存"));
    }

    @Tool(name = "replace_plan_item", description = "暂存把一个已有计划项替换为相似餐食的操作。")
    public String replacePlanItem(@ToolParam(name = "planId", description = "当前用户的计划项 ID") Long planId) {
        context.beginTool();
        if (!mutationPolicy.mayReplacePlan(context.userInput())) throw new DietException("用户没有明确要求替换计划餐食");
        PlanItemResponse item = plans.findOwned(context.userId(), planId);
        context.stage(new AgentMutation.ReplacePlanItem(planId));
        context.activity(AgentActivity.staged("准备替换计划餐食", item.planDate() + " · " + item.mealPeriod().label()));
        return json.toJson(AgentToolResult.ok(Map.of("staged", true, "planId", planId), "替换操作已暂存"));
    }

    @Tool(name = "sync_shopping_list", description = "暂存根据指定周饮食计划同步购物清单的操作。")
    public String syncShoppingList(@ToolParam(name = "weekStart", required = false, description = "周内任意 ISO 日期，默认本周") String weekStart) {
        context.beginTool();
        if (!mutationPolicy.maySyncShopping(context.userInput())) throw new DietException("用户没有明确要求同步购物清单");
        LocalDate date = parseDate(weekStart, LocalDate.now());
        context.stage(new AgentMutation.SyncShoppingList(date));
        context.activity(AgentActivity.staged("准备同步购物清单", plans.weekStart(date).toString()));
        return json.toJson(AgentToolResult.ok(Map.of("staged", true, "weekStart", plans.weekStart(date)), "购物清单同步已暂存"));
    }

    @Tool(name = "build_today_plan", description = "从之前最近一个有安排的日期复用餐食，批量生成目标日期计划草案；只暂存，不直接写库。")
    public String buildTodayPlan(
            @ToolParam(name = "targetDate", required = false, description = "目标 ISO 日期，默认今天") String targetDate,
            @ToolParam(name = "referenceDate", required = false, description = "参考 ISO 日期，默认目标日期前一天") String referenceDate) {
        context.beginTool();
        if (!mutationPolicy.mayAddPlan(context.userInput()))
            return json.toJson(AgentToolResult.retryable("WRITE_NOT_EXPLICIT", "用户没有明确要求填写饮食计划"));
        LocalDate target = parseDate(targetDate, LocalDate.now());
        LocalDate reference = parseDate(referenceDate, target.minusDays(1));
        List<PlanItemResponse> candidates = new ArrayList<>();
        LocalDate sourceDate = null;
        for (int weeksBack = 0; weeksBack < 5 && sourceDate == null; weeksBack++) {
            LocalDate lookup = reference.minusWeeks(weeksBack);
            List<PlanItemResponse> week = plans.findWeek(context.userId(), lookup);
            candidates.addAll(week);
            sourceDate = week.stream().map(PlanItemResponse::planDate)
                    .filter(value -> value.isBefore(target) && !value.isAfter(reference))
                    .max(LocalDate::compareTo).orElse(null);
        }
        if (sourceDate == null)
            return json.toJson(new AgentToolResult<>(false, "NO_MATCH", null, false, "之前没有可复用的每日计划"));
        LocalDate resolvedSourceDate = sourceDate;
        List<PlanItemResponse> sourceItems = candidates.stream().filter(item -> item.planDate().equals(resolvedSourceDate))
                .filter(item -> item.meal() != null && sourceAllowedResponse(item.meal()))
                .filter(item -> isAccessible(item.mealId())).toList();
        for (PlanItemResponse item : sourceItems) {
            context.rememberMeals(List.of(item.mealId()));
            context.stage(new AgentMutation.AddPlanItem(target, item.mealPeriod(), item.mealId(),
                    item.acquisitionMode(), item.servings()));
        }
        context.activity(AgentActivity.staged("生成今日计划草案", "从 " + resolvedSourceDate + " 复用 " + sourceItems.size() + " 个餐次"));
        return json.toJson(AgentToolResult.ok(Map.of("sourceDate", resolvedSourceDate, "targetDate", target,
                "mealPeriods", sourceItems.stream().map(item -> item.mealPeriod().name()).toList()), "今日计划草案已暂存"));
    }

    @Tool(name = "build_week_plan", description = "根据当前可访问餐食、餐次和长期偏好一次生成一周三餐草案；批量操作只暂存并等待确认。")
    public String buildWeekPlan(
            @ToolParam(name = "weekStart", required = false, description = "周内任意 ISO 日期，默认本周") String weekStart,
            @ToolParam(name = "acquisitionMode", required = false, description = "COOK、EAT_OUT 或 BOTH") String acquisitionMode) {
        context.beginTool();
        if (!mutationPolicy.mayAddPlan(context.userInput()))
            return json.toJson(AgentToolResult.retryable("WRITE_NOT_EXPLICIT", "用户没有明确要求生成饮食计划"));
        LocalDate start = plans.weekStart(parseDate(weekStart, LocalDate.now()));
        AcquisitionMode requestedMode;
        try { requestedMode = parseMode(acquisitionMode); }
        catch (DietException error) { return json.toJson(AgentToolResult.retryable("INVALID_ARGUMENT", error.getMessage())); }
        Set<Long> disliked = new LinkedHashSet<>(memories.dislikedMealIds(context.userId()));
        UserPreferenceProfile preferences = memories.preferences(context.userId());
        List<MealItem> pool = meals.findAccessibleMeals(context.userId()).stream()
                .filter(this::sourceAllowed).filter(meal -> !disliked.contains(meal.id()))
                .filter(meal -> requestedMode == null || meal.detail().acquisitionMode() == AcquisitionMode.BOTH
                        || meal.detail().acquisitionMode() == requestedMode)
                .peek(meal -> meal.matchScore(score(meal, null, null, null, preferences, Set.of(), Set.of())))
                .sorted(Comparator.comparingDouble((MealItem meal) -> meal.matchScore()).reversed()).toList();
        if (pool.isEmpty()) return json.toJson(new AgentToolResult<>(false, "NO_MATCH", null, false, "当前餐食库没有可生成计划的餐食"));
        List<Map<String, Object>> draft = new ArrayList<>();
        int cursor = 0;
        for (int day = 0; day < 7; day++) {
            for (MealPeriod period : List.of(MealPeriod.BREAKFAST, MealPeriod.LUNCH, MealPeriod.DINNER)) {
                List<MealItem> periodMeals = pool.stream().filter(meal -> supportsPeriod(meal, period)).toList();
                if (periodMeals.isEmpty()) continue;
                MealItem meal = periodMeals.get(cursor++ % periodMeals.size());
                AcquisitionMode mode = requestedMode == null ? preferredMode(meal) : requestedMode;
                context.rememberMeals(List.of(meal.id()));
                context.stage(new AgentMutation.AddPlanItem(start.plusDays(day), period, meal.id(), mode, 1));
                draft.add(Map.of("date", start.plusDays(day), "period", period, "mealId", meal.id(), "mealName", meal.name()));
            }
        }
        context.activity(AgentActivity.staged("生成一周计划草案", "共准备 " + draft.size() + " 个餐次"));
        return json.toJson(AgentToolResult.ok(draft, "一周计划草案已生成，等待用户确认"));
    }

    @Tool(name = "manage_shopping_item", description = "暂存添加、更新、勾选或删除当前用户购物项的操作。operation 为 ADD、UPDATE、CHECK 或 DELETE。")
    public String manageShoppingItem(
            @ToolParam(name = "operation") String operation,
            @ToolParam(name = "itemId", required = false) Long itemId,
            @ToolParam(name = "weekStart", required = false) String weekStart,
            @ToolParam(name = "name", required = false) String name,
            @ToolParam(name = "category", required = false) String category,
            @ToolParam(name = "quantity", required = false) BigDecimal quantity,
            @ToolParam(name = "unit", required = false) String unit,
            @ToolParam(name = "completed", required = false) Boolean completed) {
        context.beginTool();
        if (!mutationPolicy.mayModifyShopping(context.userInput()))
            return json.toJson(AgentToolResult.retryable("WRITE_NOT_EXPLICIT", "用户没有明确要求修改购物清单"));
        try {
            String action = required(operation).toUpperCase(Locale.ROOT);
            ShoppingItemRequest request = new ShoppingItemRequest(name, category, quantity, unit, completed);
            switch (action) {
                case "ADD" -> context.stage(new AgentMutation.AddShoppingItem(parseDate(weekStart, LocalDate.now()), request));
                case "UPDATE", "CHECK" -> context.stage(new AgentMutation.UpdateShoppingItem(requireId(itemId), request));
                case "DELETE" -> context.stage(new AgentMutation.DeleteShoppingItem(requireId(itemId)));
                default -> { return json.toJson(AgentToolResult.retryable("INVALID_ARGUMENT", "operation 必须是 ADD、UPDATE、CHECK 或 DELETE")); }
            }
            context.activity(AgentActivity.staged("准备修改购物清单", action));
            return json.toJson(AgentToolResult.ok(Map.of("staged", true, "operation", action), "购物清单操作已暂存"));
        } catch (DietException error) {
            return json.toJson(AgentToolResult.retryable("INVALID_ARGUMENT", error.getMessage()));
        }
    }

    @Tool(name = "check_in_meal", description = "为当前用户的一个计划项记录已吃或跳过，可附评分、饱腹感、原因和备注。")
    public String checkInMeal(
            @ToolParam(name = "planId") Long planId,
            @ToolParam(name = "skipped", required = false) Boolean skipped,
            @ToolParam(name = "rating", required = false) Integer rating,
            @ToolParam(name = "satiety", required = false) Integer satiety,
            @ToolParam(name = "reasonCode", required = false) String reasonCode,
            @ToolParam(name = "note", required = false) String note,
            @ToolParam(name = "actualSpent", required = false) BigDecimal actualSpent) {
        context.beginTool();
        if (!mutationPolicy.mayCheckIn(context.userInput()))
            return json.toJson(AgentToolResult.retryable("WRITE_NOT_EXPLICIT", "用户没有明确要求打卡或跳过"));
        try {
            plans.findOwned(context.userId(), requireId(planId));
            if (rating != null && (rating < 1 || rating > 5)) throw new DietException("评分需在 1~5 之间");
            if (satiety != null && (satiety < 1 || satiety > 5)) throw new DietException("饱腹感需在 1~5 之间");
            context.stage(new AgentMutation.CheckInPlanItem(planId,
                    new MealCheckinRequest(null, null, rating, satiety, reasonCode, note, actualSpent, null, skipped)));
            context.activity(AgentActivity.staged("准备记录饮食打卡", "计划项 #" + planId));
            return json.toJson(AgentToolResult.ok(Map.of("staged", true, "planId", planId), "打卡已暂存"));
        } catch (DietException error) {
            return json.toJson(AgentToolResult.retryable("INVALID_ARGUMENT", error.getMessage()));
        }
    }

    @Tool(name = "set_favorite", description = "收藏或取消收藏一项已检索的真实餐食。")
    public String setFavorite(@ToolParam(name = "mealId") Long mealId,
                              @ToolParam(name = "favorite") Boolean favorite) {
        context.beginTool();
        if (!mutationPolicy.mayFavorite(context.userInput()))
            return json.toJson(AgentToolResult.retryable("WRITE_NOT_EXPLICIT", "用户没有明确要求修改收藏"));
        MealItem meal = meals.findAccessibleMeal(context.userId(), mealId);
        if (meal == null || !sourceAllowed(meal))
            return json.toJson(AgentToolResult.retryable("INVALID_MEAL", "餐食不存在或无权访问"));
        context.rememberMeals(List.of(mealId));
        context.stage(new AgentMutation.SetFavorite(mealId, !Boolean.FALSE.equals(favorite), null));
        context.activity(AgentActivity.staged("准备修改收藏", meal.name()));
        return json.toJson(AgentToolResult.ok(Map.of("staged", true, "mealId", mealId), "收藏操作已暂存"));
    }

    @Tool(name = "update_diet_preference", description = "增加、移除或替换明确的长期偏好。key 为 healthGoal、cuisine、taste、convenience；operation 为 ADD、REMOVE、REPLACE。")
    public String updateDietPreference(@ToolParam(name = "operation") String operation,
                                       @ToolParam(name = "key") String key,
                                       @ToolParam(name = "value") String value) {
        context.beginTool();
        if (!mutationPolicy.mayModifyPreference(context.userInput()))
            return json.toJson(AgentToolResult.retryable("WRITE_NOT_EXPLICIT", "用户没有明确要求修改长期偏好"));
        String action = required(operation).toUpperCase(Locale.ROOT);
        String safeKey = required(key);
        String safeValue = required(value);
        UserPreferenceProfile current = memories.preferences(context.userId());
        List<String> health = new ArrayList<>(current.healthGoal());
        List<String> cuisine = new ArrayList<>(current.cuisine());
        List<String> taste = new ArrayList<>(current.taste());
        List<String> convenience = new ArrayList<>(current.convenience());
        List<String> target = switch (safeKey) {
            case "healthGoal" -> health; case "cuisine" -> cuisine; case "taste" -> taste;
            case "convenience" -> convenience; default -> null;
        };
        if (target == null) return json.toJson(AgentToolResult.retryable("INVALID_ARGUMENT", "不支持的偏好字段"));
        boolean destructive = !"ADD".equals(action);
        if ("ADD".equals(action)) { if (!target.contains(safeValue)) target.add(safeValue); }
        else if ("REMOVE".equals(action)) target.remove(safeValue);
        else if ("REPLACE".equals(action)) { target.clear(); target.add(safeValue); }
        else return json.toJson(AgentToolResult.retryable("INVALID_ARGUMENT", "operation 必须是 ADD、REMOVE 或 REPLACE"));
        UserPreferenceRequest next = new UserPreferenceRequest(health, cuisine, taste, convenience);
        context.stage(new AgentMutation.ReplacePreferences(next, destructive));
        context.activity(AgentActivity.staged("准备更新长期偏好", safeKey + " · " + safeValue));
        return json.toJson(AgentToolResult.ok(next, destructive ? "偏好变更已暂存，等待确认" : "偏好新增已暂存"));
    }

    private AcquisitionMode parseMode(String value) {
        String cleaned = clean(value);
        if (cleaned == null || "BOTH".equalsIgnoreCase(cleaned)) return null;
        try { return AcquisitionMode.valueOf(cleaned.toUpperCase(Locale.ROOT)); }
        catch (Exception error) { throw new DietException("获取方式必须是 COOK、EAT_OUT 或 BOTH"); }
    }

    private boolean isAccessible(Long mealId) {
        MealItem meal = meals.findAccessibleMeal(context.userId(), mealId);
        return meal != null && sourceAllowed(meal);
    }

    private boolean sourceAllowed(MealItem meal) {
        return meal != null && (context.sourceStrategy() == com.diet.enums.SourceStrategy.UNIFIED
                || meal.sourceType() == context.sourceMode());
    }

    private boolean sourceAllowedResponse(MealResponse meal) {
        return meal != null && (context.sourceStrategy() == com.diet.enums.SourceStrategy.UNIFIED
                || meal.sourceType() == context.sourceMode());
    }

    private boolean matches(String actual, String expected) {
        return clean(expected) == null || actual != null
                && actual.toLowerCase(Locale.ROOT).contains(expected.trim().toLowerCase(Locale.ROOT));
    }

    private boolean tagMatches(List<String> values, String expected, String wildcard) {
        return clean(expected) == null || values != null
                && (values.contains(expected.trim()) || values.contains(wildcard));
    }

    private double score(MealItem meal, String taste, String cuisine, String healthGoal,
                         UserPreferenceProfile preferences, Set<Long> favoriteIds, Set<Long> recentIds) {
        double result = 0;
        if (tagMatches(meal.slots().taste(), taste, "全部")) result += clean(taste) == null ? 0 : 10;
        if (tagMatches(meal.slots().cuisine(), cuisine, "全部")) result += clean(cuisine) == null ? 0 : 10;
        if (tagMatches(meal.slots().healthGoal(), healthGoal, "全部")) result += clean(healthGoal) == null ? 0 : 10;
        result += overlap(meal.slots().taste(), preferences.taste()) * 3;
        result += overlap(meal.slots().cuisine(), preferences.cuisine()) * 3;
        result += overlap(meal.slots().healthGoal(), preferences.healthGoal()) * 3;
        result += overlap(meal.slots().convenience(), preferences.convenience()) * 3;
        if (favoriteIds.contains(meal.id())) result += 4;
        if (meal.sourceType() == com.diet.enums.SourceMode.PERSONAL) result += 2;
        if (recentIds.contains(meal.id())) result -= 8;
        return result;
    }

    private int overlap(List<String> left, List<String> right) {
        if (left == null || right == null || left.isEmpty() || right.isEmpty()) return 0;
        Set<String> values = new LinkedHashSet<>(left);
        values.retainAll(right);
        return values.size();
    }

    private boolean supportsPeriod(MealItem meal, MealPeriod period) {
        String label = period.label();
        return meal.slots().mealTime().contains(label) || meal.slots().mealTime().contains("三餐");
    }

    private AcquisitionMode preferredMode(MealItem meal) {
        AcquisitionMode mode = meal.detail().acquisitionMode();
        return mode == null || mode == AcquisitionMode.BOTH ? AcquisitionMode.COOK : mode;
    }

    private Long requireId(Long value) {
        if (value == null || value <= 0) throw new DietException("ID 不能为空");
        return value;
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
