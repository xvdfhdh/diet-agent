package com.diet.model;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class RecommendationHistoryRow {
    private Long id;
    private Long userId;
    private String sessionId;
    private String traceId;
    private String sourceMode;
    private String userInput;
    private String slotsJson;
    private String speechText;
    private String mealsJson;
    private LocalDateTime createdAt;
}
