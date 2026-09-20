package com.diet.service.meal;

import com.diet.enums.AcquisitionMode;
import com.diet.enums.SourceMode;
import com.diet.model.*;
import com.diet.service.memory.UserMemoryService;
import com.diet.service.model.ModelConfigService;
import com.diet.service.slot.SlotOptionService;
import com.diet.util.JsonService;
import com.diet.util.LlmJsonService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class MealAiServiceTest {
    private SlotOptionService slotOptions;
    private UserMemoryService memories;
    private MealService meals;
    private TestMealAiService service;

    @BeforeEach
    void setUp() {
        slotOptions = mock(SlotOptionService.class);
        memories = mock(UserMemoryService.class);
        meals = mock(MealService.class);
        ObjectMapper mapper = new ObjectMapper();
        when(slotOptions.findAllOptions()).thenReturn(Map.of(
                "mealTime", List.of("早餐", "午餐", "晚餐"), "mood", List.of(), "scene", List.of(),
                "healthGoal", List.of("高蛋白"), "cuisine", List.of("家常"), "taste", List.of("清淡"),
                "convenience", List.of("快速")));
        service = new TestMealAiService(slotOptions, memories, meals,
                new LlmJsonService(mapper), new JsonService(mapper));
    }

    @Test
    void completionPreservesUserInputAndSanitizesGeneratedValues() {
        MealRequest current = new MealRequest("番茄牛肉饭", "/mine.jpg", List.of("晚餐"), List.of(), List.of(),
                List.of(), List.of(), List.of("清淡"), List.of(), null, null, null, null, null, 2,
                List.of(), List.of(), null, List.of(), null);
        service.response = """
                {"name":"不应覆盖","imageUrl":"https://invented.example/a.jpg","mealTime":["非法餐次"],
                 "healthGoal":["高蛋白"],"cuisine":["家常"],"taste":["重辣"],"convenience":["快速"],
                 "acquisitionMode":"COOK","prepMinutes":30,"difficulty":"魔鬼级",
                 "priceMin":40,"priceMax":20,"defaultServings":99,
                 "ingredients":[{"name":"牛肉","category":"肉蛋奶","quantity":-1,"unit":"克"}],
                 "steps":["煎牛肉"],"nutrition":{"calories":-10,"protein":30,"fat":-2,"carbs":50}}
                """;

        MealRequest result = service.complete(current);

        assertThat(result.name()).isEqualTo("番茄牛肉饭");
        assertThat(result.imageUrl()).isEqualTo("/mine.jpg");
        assertThat(result.mealTime()).containsExactly("晚餐");
        assertThat(result.taste()).containsExactly("清淡");
        assertThat(result.acquisitionMode()).isEqualTo(AcquisitionMode.COOK);
        assertThat(result.priceMin()).isEqualByComparingTo("20");
        assertThat(result.priceMax()).isEqualByComparingTo("40");
        assertThat(result.defaultServings()).isEqualTo(2);
        assertThat(result.difficulty()).isNull();
        assertThat(result.ingredients().get(0).quantity()).isNull();
        assertThat(result.nutrition().calories()).isNull();
        assertThat(result.nutrition().protein()).isEqualByComparingTo("30");
    }

    @Test
    void expansionCombinesLongTermAndFreeTextPreferencesAndSkipsDuplicateNames() {
        when(memories.preferences(7L)).thenReturn(new UserPreferenceProfile(
                List.of("高蛋白"), List.of("家常"), List.of("清淡"), List.of("快速")));
        MealItem existing = new MealItem(1L, SourceMode.PERSONAL, 7L, "已有餐", null,
                SlotBundle.empty(), MealDetail.defaults(), 0);
        when(meals.findPersonalMeals(7L)).thenReturn(List.of(existing));
        when(meals.createPersonalMeal(eq(7L), any())).thenAnswer(invocation -> {
            MealRequest request = invocation.getArgument(1);
            return new MealItem(2L, SourceMode.PERSONAL, 7L, request.name(), request.imageUrl(),
                    request.toSlots(), request.toDetail(), 0);
        });
        service.response = """
                {"meals":[
                  {"name":" 已有餐 ","mealTime":["午餐"]},
                  {"name":"鸡肉蔬菜便当","mealTime":["午餐"],"taste":["清淡"],"healthGoal":["高蛋白"],
                   "convenience":["快速"],"acquisitionMode":"COOK","ingredients":[],"steps":["煎鸡肉"]}
                ]}
                """;

        List<MealItem> created = service.expandPersonal(7L, new MealAiExpandRequest("工作日带饭，预算二十元", 3));

        assertThat(created).extracting(MealItem::name).containsExactly("鸡肉蔬菜便当");
        assertThat(service.lastPrompt).contains("高蛋白", "工作日带饭，预算二十元", "已有餐");
        verify(meals, times(1)).createPersonalMeal(eq(7L), any());
    }

    private static final class TestMealAiService extends MealAiService {
        private String response;
        private String lastPrompt;

        private TestMealAiService(SlotOptionService slotOptions, UserMemoryService memories, MealService meals,
                                  LlmJsonService llmJson, JsonService json) {
            super(mock(ModelConfigService.class), slotOptions, memories, meals, llmJson, json);
        }

        @Override
        protected String call(String prompt) {
            lastPrompt = prompt;
            return response;
        }
    }
}
