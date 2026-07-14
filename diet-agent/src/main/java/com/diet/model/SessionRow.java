package com.diet.model;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SessionRow {
    private String id;
    private Long userId;
    private String phase;
    private String slots;
    private String lastRecommendations;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}




