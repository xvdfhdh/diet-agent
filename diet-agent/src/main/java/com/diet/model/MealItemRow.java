package com.diet.model;

import lombok.Data;

import java.time.LocalDateTime;
import java.math.BigDecimal;

@Data
public class MealItemRow {
    private Long id;
    private String sourceType;
    private Long ownerUserId;
    private String name;
    private String imageUrl;
    private String mealTime;
    private String mood;
    private String scene;
    private String healthGoal;
    private String cuisine;
    private String taste;
    private String convenience;
    private String acquisitionMode;
    private Integer prepMinutes;
    private String difficulty;
    private BigDecimal priceMin;
    private BigDecimal priceMax;
    private Integer defaultServings;
    private String ingredientsJson;
    private String stepsJson;
    private String dineOutTips;
    private String substitutesJson;
    private Integer calories;
    private BigDecimal protein;
    private BigDecimal fat;
    private BigDecimal carbs;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
