package com.diet.model;

import com.diet.enums.SourceMode;
import java.time.LocalDateTime;

public record SessionSummaryResponse(
        String id,
        String phase,
        SourceMode sourceMode,
        String preview,
        int messageCount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) { }
