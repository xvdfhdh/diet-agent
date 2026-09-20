package com.diet.model;

import java.math.BigDecimal;

public record ShoppingItemRequest(
        String name,
        String category,
        BigDecimal quantity,
        String unit,
        Boolean completed
) { }
