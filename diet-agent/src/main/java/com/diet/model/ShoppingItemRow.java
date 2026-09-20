package com.diet.model;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class ShoppingItemRow {
    private Long id;
    private Long listId;
    private Long userId;
    private String name;
    private String category;
    private BigDecimal quantity;
    private String unit;
    private String sourceMealIds;
    private Boolean manual;
    private Boolean completed;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
