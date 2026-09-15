package com.diet.model;

public record ModelProviderTemplate(
        String id,
        String name,
        String baseUrl,
        String endpointPath,
        String mainModel,
        String lightModel,
        String helpUrl
) {
}
