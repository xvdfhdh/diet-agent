package com.diet.model;

import java.math.BigDecimal;

public record NutritionSummary(Integer calories, BigDecimal protein, BigDecimal fat, BigDecimal carbs) { }
