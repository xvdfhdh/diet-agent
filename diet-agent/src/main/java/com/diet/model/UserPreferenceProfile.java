package com.diet.model;

import java.util.List;

public record UserPreferenceProfile(
        List<String> healthGoal,
        List<String> cuisine,
        List<String> taste,
        List<String> convenience
) {
    public static UserPreferenceProfile empty() {
        return new UserPreferenceProfile(List.of(), List.of(), List.of(), List.of());
    }
}
