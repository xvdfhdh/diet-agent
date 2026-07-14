package com.diet.model;

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
public class FeedbackRequest {
    private String sessionId;
    private Long itemId;
    private String action;
    private Integer rating;
    private String reason;
}