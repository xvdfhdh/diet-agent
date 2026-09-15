package com.diet.model;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class FavoriteMealRow {
    private Long id;
    private Long userId;
    private Long mealId;
    private String sessionId;
    private String mealJson;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
