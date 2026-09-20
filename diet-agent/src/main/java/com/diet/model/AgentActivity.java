package com.diet.model;

public record AgentActivity(String name, String status, String detail) {
    public static AgentActivity completed(String name, String detail) {
        return new AgentActivity(name, "COMPLETED", detail);
    }

    public static AgentActivity staged(String name, String detail) {
        return new AgentActivity(name, "STAGED", detail);
    }
}
