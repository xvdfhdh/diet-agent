package com.diet.model;

import java.time.LocalDateTime;

public record FavoriteMealResponse(MealResponse meal, LocalDateTime createdAt) {
}
