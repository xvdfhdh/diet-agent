package com.diet.model;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class RequestTraceRow {
    private Long id;
    private String traceId;
    private String sessionId;
    private Long userId;
    private String status;
    private Integer eventCount;
    private Long durationMs;
    private String errorMessage;
    private String traceJson;
    private String expectedIntent;
    private String expectedSlots;
    private String expectedClarifyAction;
    private Long labeledBy;
    private LocalDateTime labeledAt;
    private String labelNote;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}