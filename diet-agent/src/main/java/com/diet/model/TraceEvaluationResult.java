package com.diet.model;

import java.time.LocalDateTime;
import java.util.Map;

public record TraceEvaluationResult(
        String traceId,
        String sessionId,
        LocalDateTime createdAt,
        Double score,
        Double ruleScore,
        Double llmJudgeScore,
        Double userFeedbackScore,
        Map<String, Double> metrics,
        Map<String, Object> detail
) {
}




