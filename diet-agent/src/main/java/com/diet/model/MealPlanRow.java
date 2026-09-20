package com.diet.model;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class MealPlanRow {
    private Long id;
    private Long userId;
    private LocalDate planDate;
    private String mealPeriod;
    private Long mealId;
    private String mealSnapshot;
    private String acquisitionMode;
    private Integer servings;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
