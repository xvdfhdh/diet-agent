package com.diet.model;

import java.util.Map;

import com.diet.enums.SourceMode;
import com.diet.enums.RecommendationMode;
import com.diet.enums.SourceStrategy;
import com.fasterxml.jackson.annotation.JsonAutoDetect;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
@Accessors(fluent = true)
@AllArgsConstructor
@NoArgsConstructor
public class ChatRequest {
    private String sessionId;
    private String message;
    private SourceMode sourceMode;
    private RecommendationMode recommendationMode;
    private SourceStrategy sourceStrategy;
    private Map<String, Object> context;

    public ChatRequest(String sessionId, String message, SourceMode sourceMode,
                       RecommendationMode recommendationMode, Map<String, Object> context) {
        this(sessionId, message, sourceMode, recommendationMode, null, context);
    }
}
