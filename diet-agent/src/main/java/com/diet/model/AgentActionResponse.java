package com.diet.model;

import com.diet.enums.PendingActionStatus;
import java.util.List;

public record AgentActionResponse(
        String id,
        PendingActionStatus status,
        String message,
        List<String> affectedDomains
) {
    public AgentActionResponse {
        affectedDomains = affectedDomains == null ? List.of() : List.copyOf(affectedDomains);
    }
}
