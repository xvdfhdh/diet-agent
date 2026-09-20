package com.diet.model;

import java.time.LocalDateTime;

public record SessionMessageResponse(
        Long id,
        String role,
        String content,
        String intent,
        String traceId,
        LocalDateTime createdAt
) { }
