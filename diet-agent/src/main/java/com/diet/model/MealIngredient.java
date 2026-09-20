package com.diet.model;

import java.math.BigDecimal;

public record MealIngredient(String name, String category, BigDecimal quantity, String unit) { }
