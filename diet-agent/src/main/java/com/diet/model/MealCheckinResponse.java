package com.diet.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record MealCheckinResponse(
        Long id,
        Long actualMealId,
        String actualMealName,
        Integer rating,
        Integer satiety,
        String reasonCode,
        String note,
        BigDecimal actualSpent,
        LocalDateTime eatenAt
) { }
