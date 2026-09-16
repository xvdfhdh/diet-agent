package com.diet.model;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class DietUserRow {
    private Long id;
    private String username;
    private String passwordHash;
    private String role;
    private int tokenVersion;
    private boolean enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
