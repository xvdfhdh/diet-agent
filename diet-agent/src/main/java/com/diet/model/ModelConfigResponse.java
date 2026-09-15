package com.diet.model;

import java.util.List;

public record ModelConfigResponse(
        String providerId,
        String displayName,
        String baseUrl,
        String endpointPath,
        String mainModel,
        String lightModel,
        boolean apiKeyConfigured,
        String apiKeyMasked,
        List<ModelProviderTemplate> templates
) {
}
