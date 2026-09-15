package com.diet.model;

import java.util.List;

public record UserPreferenceRequest(
        List<String> healthGoal,
        List<String> cuisine,
        List<String> taste,
        List<String> convenience
) {
}
