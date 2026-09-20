package com.diet.model;

import com.diet.enums.AcquisitionMode;
import com.diet.enums.MealPeriod;

import java.time.LocalDate;

public record PlanItemRequest(
        LocalDate planDate,
        MealPeriod mealPeriod,
        Long mealId,
        AcquisitionMode acquisitionMode,
        Integer servings
) { }
