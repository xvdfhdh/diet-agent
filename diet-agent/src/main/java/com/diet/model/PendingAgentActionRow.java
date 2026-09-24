package com.diet.model;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class PendingAgentActionRow {
    private String id;
    private Long userId;
    private String sessionId;
    private String taskType;
    private String actionJson;
    private String previewJson;
    private String preconditionHash;
    private String status;
    private LocalDateTime expiresAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
