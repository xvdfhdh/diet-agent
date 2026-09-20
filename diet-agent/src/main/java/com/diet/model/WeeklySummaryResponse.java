package com.diet.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record WeeklySummaryResponse(
        LocalDate weekStart,
        int plannedCount,
        int completedCount,
        int skippedCount,
        double completionRate,
        int cookCount,
        int eatOutCount,
        List<String> topTastes,
        List<String> topCuisines,
        List<String> topHealthGoals,
        List<String> mostCompletedMeals,
        List<String> mostSkippedMeals,
        BigDecimal estimatedCost,
        BigDecimal actualCost
) { }
