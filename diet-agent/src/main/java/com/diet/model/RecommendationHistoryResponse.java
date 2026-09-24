package com.diet.model;

import com.diet.enums.SourceMode;
import com.diet.enums.SourceStrategy;

import java.time.LocalDateTime;
import java.util.List;

public record RecommendationHistoryResponse(
        Long id,
        String sessionId,
        String traceId,
        SourceMode sourceMode,
        SourceStrategy sourceStrategy,
        String userInput,
        SlotBundle slots,
        String speechText,
        List<MealResponse> meals,
        LocalDateTime createdAt
) {
}
