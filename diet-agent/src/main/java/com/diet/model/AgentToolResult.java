package com.diet.model;

public record AgentToolResult<T>(boolean success, String code, T data, boolean retryable, String message) {
    public static <T> AgentToolResult<T> ok(T data, String message) {
        return new AgentToolResult<>(true, "OK", data, false, message);
    }

    public static <T> AgentToolResult<T> retryable(String code, String message) {
        return new AgentToolResult<>(false, code, null, true, message);
    }
}
