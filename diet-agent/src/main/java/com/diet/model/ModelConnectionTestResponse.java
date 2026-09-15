package com.diet.model;

public record ModelConnectionTestResponse(boolean success, long latencyMs, String message) {
}
