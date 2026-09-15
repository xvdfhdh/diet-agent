package com.diet.model;

import com.diet.enums.SourceMode;

import java.time.LocalDateTime;
import java.util.List;

public record RecommendationHistoryResponse(
        Long id,
        String sessionId,
        String traceId,
        SourceMode sourceMode,
        String userInput,
        SlotBundle slots,
        String speechText,
        List<MealResponse> meals,
        LocalDateTime createdAt
) {
}
