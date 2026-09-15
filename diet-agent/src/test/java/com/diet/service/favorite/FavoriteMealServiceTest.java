package com.diet.service.favorite;

import com.diet.enums.SourceMode;
import com.diet.mapper.FavoriteMealMapper;
import com.diet.model.FavoriteMealRow;
import com.diet.model.MealItem;
import com.diet.model.SlotBundle;
import com.diet.service.meal.MealService;
import com.diet.service.memory.UserMemoryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FavoriteMealServiceTest {
    @Test
    void favoriteStoresSnapshotAndStrengthensLongTermMemory() {
        FavoriteMealMapper mapper = mock(FavoriteMealMapper.class);
        MealService mealService = mock(MealService.class);
        UserMemoryService memoryService = mock(UserMemoryService.class);
        FavoriteMealService service = new FavoriteMealService(mapper, mealService, memoryService, new ObjectMapper());
        MealItem meal = new MealItem(
                4L, SourceMode.PUBLIC, null, "麻辣香锅", "/meals/spicy-dry-pot.png",
                new SlotBundle(List.of("晚餐"), List.of(), List.of(), List.of("补能"),
                        List.of("川菜"), List.of("麻辣"), List.of("多人共享")), 0.8);
        when(mealService.findAccessibleMeal(7L, 4L)).thenReturn(meal);

        var response = service.add(7L, 4L, "session-1");

        ArgumentCaptor<FavoriteMealRow> captor = ArgumentCaptor.forClass(FavoriteMealRow.class);
        verify(mapper).upsert(captor.capture());
        assertThat(captor.getValue().getMealJson()).contains("麻辣香锅", "spicy-dry-pot.png");
        verify(memoryService).rememberFeedback(7L, "session-1", meal, "FAVORITE");
        assertThat(response.meal().imageUrl()).isEqualTo("/meals/spicy-dry-pot.png");
    }
}
