package com.diet.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record ShoppingListResponse(
        Long id,
        LocalDate weekStart,
        Integer syncVersion,
        String status,
        LocalDateTime syncedAt,
        boolean needsSync,
        List<ShoppingItemResponse> items
) { }
