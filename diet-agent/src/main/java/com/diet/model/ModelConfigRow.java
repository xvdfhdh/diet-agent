package com.diet.model;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ModelConfigRow {
    private Long id;
    private String providerId;
    private String displayName;
    private String baseUrl;
    private String endpointPath;
    private String mainModel;
    private String lightModel;
    private String apiKey;
    private LocalDateTime updatedAt;
}
