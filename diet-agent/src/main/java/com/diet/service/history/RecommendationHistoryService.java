package com.diet.service.history;

import com.diet.enums.SourceMode;
import com.diet.enums.SourceStrategy;
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
import java.util.Locale;
import java.util.Optional;

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
        record(userId, sessionId, traceId, sourceMode, SourceStrategy.SELECTED_ONLY,
                userInput, slots, speechText, meals);
    }

    public void record(
            Long userId, String sessionId, String traceId, SourceMode sourceMode,
            SourceStrategy sourceStrategy, String userInput, SlotBundle slots,
            String speechText, List<MealResponse> meals
    ) {
        if (meals == null || meals.isEmpty()) {
            return;
        }
        RecommendationHistoryRow row = new RecommendationHistoryRow();
        row.setUserId(userId);
        row.setSessionId(sessionId);
        row.setTraceId(traceId);
        row.setSourceMode(sourceMode.name());
        row.setSourceStrategy((sourceStrategy == null ? SourceStrategy.SELECTED_ONLY : sourceStrategy).name());
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

    public List<RecommendationHistoryResponse> recent(Long userId, Integer limit) {
        int safeLimit = Math.max(1, Math.min(limit == null ? 20 : limit, MAX_LIMIT));
        return mapper.findRecent(userId, safeLimit).stream().map(this::toResponse).toList();
    }

    /** 识别“上次那个类似的再来一个”，并把最近一次推荐作为相似推荐锚点。 */
    public Optional<HistoryReference> resolveReference(Long userId, SourceMode sourceMode, String userInput) {
        if (!looksLikeHistoricalFollowUp(userInput)) {
            return Optional.empty();
        }
        return recent(userId, 10).stream()
                .filter(history -> history.sourceMode() == sourceMode)
                .findFirst()
                .filter(history -> history.meals() != null && !history.meals().isEmpty())
                .map(history -> {
                    MealResponse anchor = history.meals().get(0);
                    SlotBundle slots = new SlotBundle(
                            anchor.mealTime(), anchor.mood(), anchor.scene(), anchor.healthGoal(),
                            anchor.cuisine(), anchor.taste(), anchor.convenience());
                    List<Long> mealIds = history.meals().stream().map(MealResponse::id).toList();
                    return new HistoryReference(history.id(), history.sourceMode(), anchor.name(), slots, mealIds);
                });
    }

    private boolean looksLikeHistoricalFollowUp(String userInput) {
        if (userInput == null || userInput.isBlank()) {
            return false;
        }
        String text = userInput.toLowerCase(Locale.ROOT);
        boolean referencesPast = List.of("上次", "之前", "刚才", "刚刚", "那个").stream().anyMatch(text::contains);
        boolean asksSimilar = List.of("类似", "差不多", "再推荐", "再来", "换一个", "换一批").stream().anyMatch(text::contains);
        return referencesPast && asksSimilar;
    }

    public record HistoryReference(Long historyId, SourceMode sourceMode, String anchorMealName,
                                   SlotBundle slots, List<Long> excludeMealIds) {
    }

    private RecommendationHistoryResponse toResponse(RecommendationHistoryRow row) {
        try {
            return new RecommendationHistoryResponse(
                    row.getId(),
                    row.getSessionId(),
                    row.getTraceId(),
                    SourceMode.valueOf(row.getSourceMode()),
                    parseStrategy(row.getSourceStrategy()),
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

    private SourceStrategy parseStrategy(String value) {
        try { return value == null || value.isBlank() ? SourceStrategy.SELECTED_ONLY : SourceStrategy.valueOf(value); }
        catch (Exception ignored) { return SourceStrategy.SELECTED_ONLY; }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception error) {
            throw new DietException("推荐历史序列化失败", error);
        }
    }
}
