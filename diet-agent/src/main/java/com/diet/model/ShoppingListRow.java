package com.diet.model;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class ShoppingListRow {
    private Long id;
    private Long userId;
    private LocalDate weekStart;
    private Integer syncVersion;
    private String status;
    private LocalDateTime syncedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
