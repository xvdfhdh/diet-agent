package com.diet.model;

import java.time.LocalDateTime;

public record UserMemoryResponse(
        Long id,
        String type,
        String key,
        String value,
        double strength,
        int evidenceCount,
        String source,
        LocalDateTime updatedAt
) {
}
