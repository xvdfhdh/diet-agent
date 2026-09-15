package com.diet.service.history;

import com.diet.enums.SourceMode;
import com.diet.exception.DietException;
import com.diet.mapper.RecommendationHistoryMapper;
import com.diet.model.MealResponse;
import com.diet.model.RecommendationHistoryResponse;
import com.diet.model.RecommendationHistoryRow;
import com.diet.model.SlotBundle;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RecommendationHistoryService {
    private static final TypeReference<List<MealResponse>> MEAL_LIST = new TypeReference<>() {
    };
    private static final int MAX_LIMIT = 50;

    private final RecommendationHistoryMapper mapper;
    private final ObjectMapper objectMapper;

    public RecommendationHistoryService(RecommendationHistoryMapper mapper, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    public void record(
            Long userId,
            String sessionId,
            String traceId,
            SourceMode sourceMode,
            String userInput,
            SlotBundle slots,
            String speechText,
            List<MealResponse> meals
    ) {
        if (meals == null || meals.isEmpty()) {
            return;
        }
        RecommendationHistoryRow row = new RecommendationHistoryRow();
        row.setUserId(userId);
        row.setSessionId(sessionId);
        row.setTraceId(traceId);
        row.setSourceMode(sourceMode.name());
        row.setUserInput(userInput);
        row.setSlotsJson(writeJson(slots == null ? SlotBundle.empty() : slots));
        row.setSpeechText(speechText);
        row.setMealsJson(writeJson(meals));
        mapper.insert(row);
    }

    public List<RecommendationHistoryResponse> today(Long userId, Integer limit) {
        int safeLimit = Math.max(1, Math.min(limit == null ? 20 : limit, MAX_LIMIT));
        return mapper.findToday(userId, safeLimit).stream().map(this::toResponse).toList();
    }

    private RecommendationHistoryResponse toResponse(RecommendationHistoryRow row) {
        try {
            return new RecommendationHistoryResponse(
                    row.getId(),
                    row.getSessionId(),
                    row.getTraceId(),
                    SourceMode.valueOf(row.getSourceMode()),
                    row.getUserInput(),
                    objectMapper.readValue(row.getSlotsJson(), SlotBundle.class),
                    row.getSpeechText(),
                    objectMapper.readValue(row.getMealsJson(), MEAL_LIST),
                    row.getCreatedAt()
            );
        } catch (Exception error) {
            throw new DietException("推荐历史解析失败", error);
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception error) {
            throw new DietException("推荐历史序列化失败", error);
        }
    }
}
