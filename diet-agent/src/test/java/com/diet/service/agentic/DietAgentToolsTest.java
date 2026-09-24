package com.diet.service.agentic;

import com.diet.enums.SourceMode;
import com.diet.enums.AcquisitionMode;
import com.diet.enums.MealPeriod;
import com.diet.enums.PlanStatus;
import com.diet.enums.SourceStrategy;
import com.diet.enums.AgentTaskType;
import com.diet.exception.DietException;
import com.diet.model.MealItem;
import com.diet.model.MealResponse;
import com.diet.model.PlanItemResponse;
import com.diet.model.SlotBundle;
import com.diet.service.history.RecommendationHistoryService;
import com.diet.service.meal.MealService;
import com.diet.service.memory.UserMemoryService;
import com.diet.service.plan.MealPlanService;
import com.diet.service.shopping.ShoppingListService;
import com.diet.util.JsonService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class DietAgentToolsTest {
    @Test
    void writeToolStagesCommandWithoutTouchingDatabase() {
        MealService meals = mock(MealService.class);
        MealPlanService plans = mock(MealPlanService.class);
        AgentRunContext context = new AgentRunContext(19L, SourceMode.PUBLIC,
                "把它加入明天午餐计划", ignored -> {});
        context.rememberMeals(List.of(7L));
        DietAgentTools tools = tools(context, meals, plans);

        tools.addOrReplacePlanItem(LocalDate.now().plusDays(1).toString(), "LUNCH", 7L, "COOK", 2);

        assertThat(context.stagedMutations()).containsExactly(
                new AgentMutation.AddPlanItem(LocalDate.now().plusDays(1), com.diet.enums.MealPeriod.LUNCH,
                        7L, com.diet.enums.AcquisitionMode.COOK, 2));
        verifyNoInteractions(plans);
    }

    @Test
    void writeToolRejectsVagueInputAndDetailRequiresPriorRetrieval() {
        MealService meals = mock(MealService.class);
        MealPlanService plans = mock(MealPlanService.class);
        AgentRunContext vague = new AgentRunContext(19L, SourceMode.PUBLIC, "这道不错", ignored -> {});
        vague.rememberMeals(List.of(7L));
        DietAgentTools tools = tools(vague, meals, plans);

        assertThatThrownBy(() -> tools.addOrReplacePlanItem(LocalDate.now().toString(), "LUNCH", 7L, null, 1))
                .isInstanceOf(DietException.class).hasMessageContaining("没有明确要求");
        assertThatThrownBy(() -> tools.getMealDetail(9L))
                .isInstanceOf(DietException.class).hasMessageContaining("本轮检索结果");
        verifyNoInteractions(plans);
    }

    @Test
    void searchKeepsCurrentSourceAndUserScope() {
        MealService meals = mock(MealService.class);
        UserMemoryService memories = mock(UserMemoryService.class);
        when(memories.dislikedMealIds(19L)).thenReturn(List.of());
        when(meals.findAccessibleMeals(19L)).thenReturn(List.of(
                new MealItem(1L, SourceMode.PUBLIC, null, "公共餐", null, SlotBundle.empty(), 0),
                new MealItem(2L, SourceMode.PERSONAL, 19L, "个人餐", null, SlotBundle.empty(), 0)));
        AgentRunContext context = new AgentRunContext(19L, SourceMode.PUBLIC, "推荐午餐", ignored -> {});
        DietAgentTools tools = new DietAgentTools(context, meals, memories,
                mock(RecommendationHistoryService.class), mock(MealPlanService.class),
                mock(ShoppingListService.class), new AgentMutationPolicy(), new JsonService(jsonMapper()));

        String result = tools.searchMeals(null, null, null, null, null, 10);

        assertThat(result).contains("公共餐").doesNotContain("个人餐");
        assertThat(context.wasRetrieved(1L)).isTrue();
        assertThat(context.wasRetrieved(2L)).isFalse();
        verify(meals).findAccessibleMeals(19L);
    }

    @Test
    void previousPlanMealCanBeVerifiedAndReusedForToday() {
        MealService meals = mock(MealService.class);
        MealPlanService plans = mock(MealPlanService.class);
        LocalDate yesterday = LocalDate.now().minusDays(1);
        MealItem publicMeal = new MealItem(7L, SourceMode.PUBLIC, null, "历史早餐", null, SlotBundle.empty(), 0);
        MealResponse snapshot = MealResponse.from(publicMeal);
        when(meals.findAccessibleMeal(19L, 7L)).thenReturn(publicMeal);
        when(plans.findWeek(19L, yesterday)).thenReturn(List.of(
                new PlanItemResponse(31L, yesterday, MealPeriod.BREAKFAST, 7L, snapshot,
                        AcquisitionMode.COOK, 1, PlanStatus.PLANNED, null)));
        AgentRunContext context = new AgentRunContext(19L, SourceMode.PUBLIC,
                "帮我按照之前的每日饮食计划，填写今日计划", ignored -> {});
        DietAgentTools tools = tools(context, meals, plans);

        String planJson = tools.getWeekPlan(yesterday.toString());
        tools.addOrReplacePlanItem(LocalDate.now().toString(), "BREAKFAST", 7L, "COOK", 1);

        assertThat(planJson).contains("reusableMealIds").contains("历史早餐");
        assertThat(context.wasRetrieved(7L)).isTrue();
        assertThat(context.stagedMutations()).containsExactly(
                new AgentMutation.AddPlanItem(LocalDate.now(), MealPeriod.BREAKFAST,
                        7L, AcquisitionMode.COOK, 1));
    }

    @Test
    void unifiedSearchIncludesBothSourcesAndPrefersPersonalOnEqualScore() {
        MealService meals = mock(MealService.class);
        UserMemoryService memories = mock(UserMemoryService.class);
        when(memories.dislikedMealIds(19L)).thenReturn(List.of());
        when(memories.preferences(19L)).thenReturn(new com.diet.model.UserPreferenceProfile(List.of(), List.of(), List.of(), List.of()));
        RecommendationHistoryService history = mock(RecommendationHistoryService.class);
        when(history.recent(19L, 10)).thenReturn(List.of());
        when(meals.findAccessibleMeals(19L)).thenReturn(List.of(
                new MealItem(1L, SourceMode.PUBLIC, null, "公共餐", null, SlotBundle.empty(), 0),
                new MealItem(2L, SourceMode.PERSONAL, 19L, "个人餐", null, SlotBundle.empty(), 0)));
        AgentRunContext context = new AgentRunContext(19L, SourceMode.PUBLIC, SourceStrategy.UNIFIED,
                AgentTaskType.RECOMMEND, "推荐午餐", ignored -> {});
        DietAgentTools tools = new DietAgentTools(context, meals, memories,
                history, mock(MealPlanService.class),
                mock(ShoppingListService.class), null, new AgentMutationPolicy(), new JsonService(jsonMapper()));

        String result = tools.findMealOptions(null, null, null, null, null, null, null, null, 10);

        assertThat(result).contains("公共餐").contains("个人餐");
        assertThat(result.indexOf("个人餐")).isLessThan(result.indexOf("公共餐"));
    }

    private DietAgentTools tools(AgentRunContext context, MealService meals, MealPlanService plans) {
        UserMemoryService memories = mock(UserMemoryService.class);
        when(memories.dislikedMealIds(19L)).thenReturn(List.of());
        return new DietAgentTools(context, meals, memories, mock(RecommendationHistoryService.class), plans,
                mock(ShoppingListService.class), new AgentMutationPolicy(), new JsonService(jsonMapper()));
    }

    private ObjectMapper jsonMapper() { return new ObjectMapper().findAndRegisterModules(); }
}
