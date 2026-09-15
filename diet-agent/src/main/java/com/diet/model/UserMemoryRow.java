package com.diet.model;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UserMemoryRow {
    private Long id;
    private Long userId;
    private String memoryType;
    private String memoryKey;
    private String memoryValue;
    private double strength;
    private int evidenceCount;
    private String source;
    private String lastSessionId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
