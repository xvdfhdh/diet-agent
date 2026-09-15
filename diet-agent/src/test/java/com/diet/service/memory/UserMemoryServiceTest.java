package com.diet.service.memory;

import com.diet.enums.SourceMode;
import com.diet.mapper.UserMemoryMapper;
import com.diet.model.MealItem;
import com.diet.model.SlotBundle;
import com.diet.model.UserMemoryRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserMemoryServiceTest {
    private UserMemoryMapper mapper;
    private UserMemoryService service;

    @BeforeEach
    void setUp() {
        mapper = mock(UserMemoryMapper.class);
        service = new UserMemoryService(mapper);
    }

    @Test
    void personalizeFillsOnlyMissingStablePreferences() {
        when(mapper.findPositiveSlotMemories(eq(7L), anyInt())).thenReturn(List.of(
                memory("taste", "麻辣", 5),
                memory("cuisine", "川菜", 4),
                memory("mood", "开心", 9)
        ));
        SlotBundle current = new SlotBundle(
                List.of("午餐"), List.of(), List.of(), List.of(), List.of(), List.of("清淡"), List.of()
        );

        SlotBundle personalized = service.personalize(7L, current);

        assertThat(personalized.mealTime()).containsExactly("午餐");
        assertThat(personalized.taste()).containsExactly("清淡");
        assertThat(personalized.cuisine()).containsExactly("川菜");
        assertThat(personalized.mood()).isEmpty();
    }

    @Test
    void negativeMealFeedbackBecomesARecallExclusion() {
        MealItem meal = new MealItem(
                42L, SourceMode.PUBLIC, null, "麻辣香锅",
                new SlotBundle(List.of("晚餐"), List.of(), List.of(), List.of(), List.of("川菜"), List.of("麻辣"), List.of()),
                0.8
        );

        service.rememberFeedback(7L, "session-1", meal, "DISLIKE");

        verify(mapper).upsert(7L, "MEAL_PREFERENCE", "麻辣香锅", "42", -3.0, "FEEDBACK", "session-1");
    }

    private UserMemoryRow memory(String key, String value, double strength) {
        UserMemoryRow row = new UserMemoryRow();
        row.setMemoryType("SLOT_PREFERENCE");
        row.setMemoryKey(key);
        row.setMemoryValue(value);
        row.setStrength(strength);
        return row;
    }
}
