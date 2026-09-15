package com.diet.model;

public record ModelConfigRequest(
        String providerId,
        String displayName,
        String baseUrl,
        String endpointPath,
        String mainModel,
        String lightModel,
        String apiKey
) {
}
