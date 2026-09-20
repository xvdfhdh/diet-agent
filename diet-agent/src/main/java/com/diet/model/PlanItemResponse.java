package com.diet.model;

import com.diet.enums.AcquisitionMode;
import com.diet.enums.MealPeriod;
import com.diet.enums.PlanStatus;

import java.time.LocalDate;

public record PlanItemResponse(
        Long id,
        LocalDate planDate,
        MealPeriod mealPeriod,
        Long mealId,
        MealResponse meal,
        AcquisitionMode acquisitionMode,
        Integer servings,
        PlanStatus status,
        MealCheckinResponse checkin
) { }
