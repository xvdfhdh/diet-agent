package com.diet.model;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SessionMessageRow {
    private Long id;
    private String sessionId;
    private String role;
    private String content;
    private String intent;
    private String agentTraceId;
    private LocalDateTime createdAt;
}




