package com.diet.model;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class MealItemRow {
    private Long id;
    private String sourceType;
    private Long ownerUserId;
    private String name;
    private String mealTime;
    private String mood;
    private String scene;
    private String healthGoal;
    private String cuisine;
    private String taste;
    private String convenience;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}