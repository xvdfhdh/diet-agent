package com.diet.model;

import java.time.LocalDateTime;
import java.util.List;

public record AgentActionPreview(
        String id,
        String summary,
        List<AgentActionChange> changes,
        LocalDateTime expiresAt,
        boolean requiresConfirmation
) {
    public AgentActionPreview {
        changes = changes == null ? List.of() : List.copyOf(changes);
    }
}
