package com.diet.model;

import java.math.BigDecimal;
import java.util.List;

public record ShoppingItemResponse(
        Long id,
        String name,
        String category,
        BigDecimal quantity,
        String unit,
        List<Long> sourceMealIds,
        boolean manual,
        boolean completed
) { }
