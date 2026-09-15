package com.diet.service.history;

import com.diet.enums.SourceMode;
import com.diet.mapper.RecommendationHistoryMapper;
import com.diet.model.MealResponse;
import com.diet.model.RecommendationHistoryRow;
import com.diet.model.SlotBundle;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RecommendationHistoryServiceTest {
    private RecommendationHistoryMapper mapper;
    private RecommendationHistoryService service;

    @BeforeEach
    void setUp() {
        mapper = mock(RecommendationHistoryMapper.class);
        service = new RecommendationHistoryService(mapper, new ObjectMapper());
    }

    @Test
    void recordsAndReadsRenderedRecommendations() {
        SlotBundle slots = new SlotBundle(List.of("午餐"), List.of(), List.of(), List.of(), List.of(), List.of("清淡"), List.of("快速"));
        MealResponse meal = new MealResponse(
                3L, SourceMode.PUBLIC, "鸡胸肉轻食碗", "/meals/chicken-grain-bowl.jpg", List.of("午餐"), List.of(), List.of(),
                List.of("高蛋白"), List.of("轻食"), List.of("清淡"), List.of("快速"), 0.7
        );

        service.record(1L, "session-1", "trace-1", SourceMode.PUBLIC, "午餐要清淡", slots, "推荐如下", List.of(meal));

        ArgumentCaptor<RecommendationHistoryRow> captor = ArgumentCaptor.forClass(RecommendationHistoryRow.class);
        verify(mapper).insert(captor.capture());
        RecommendationHistoryRow stored = captor.getValue();
        stored.setId(9L);
        stored.setCreatedAt(LocalDateTime.of(2026, 9, 15, 9, 30));
        when(mapper.findToday(1L, 20)).thenReturn(List.of(stored));

        var history = service.today(1L, 20);

        assertThat(history).hasSize(1);
        assertThat(history.get(0).userInput()).isEqualTo("午餐要清淡");
        assertThat(history.get(0).meals()).extracting(MealResponse::name).containsExactly("鸡胸肉轻食碗");
        assertThat(history.get(0).slots().taste()).containsExactly("清淡");
    }

    @Test
    void resolvesNaturalLanguageReferenceToLatestRecommendation() {
        MealResponse meal = new MealResponse(
                4L, SourceMode.PUBLIC, "麻辣香锅", "/meals/spicy-dry-pot.png", List.of("晚餐"), List.of("开心"),
                List.of("周末"), List.of("补能"), List.of("川菜"), List.of("麻辣"), List.of("多人共享"), 0.9);
        RecommendationHistoryRow row = new RecommendationHistoryRow();
        row.setId(11L);
        row.setSourceMode("PUBLIC");
        row.setSessionId("old-session");
        row.setTraceId("old-trace");
        row.setUserInput("想吃点辣的");
        row.setSlotsJson("{}");
        row.setSpeechText("推荐麻辣香锅");
        try {
            row.setMealsJson(new ObjectMapper().writeValueAsString(List.of(meal)));
        } catch (Exception error) {
            throw new RuntimeException(error);
        }
        row.setCreatedAt(LocalDateTime.now());
        when(mapper.findRecent(1L, 10)).thenReturn(List.of(row));

        var reference = service.resolveReference(1L, SourceMode.PUBLIC, "上次那个挺好吃，类似的再推荐一下");

        assertThat(reference).isPresent();
        assertThat(reference.orElseThrow().anchorMealName()).isEqualTo("麻辣香锅");
        assertThat(reference.orElseThrow().excludeMealIds()).containsExactly(4L);
        assertThat(reference.orElseThrow().slots().taste()).containsExactly("麻辣");
    }
}
