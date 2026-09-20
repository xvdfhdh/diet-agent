package com.diet.model;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class MealCheckinRow {
    private Long id;
    private Long userId;
    private Long planId;
    private Long actualMealId;
    private String actualMealName;
    private Integer rating;
    private Integer satiety;
    private String reasonCode;
    private String note;
    private BigDecimal actualSpent;
    private LocalDateTime eatenAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
