package com.diet.model;

public record AuthUserResponse(Long id, String username, String role) {
    public static AuthUserResponse from(DietUserRow row) {
        return new AuthUserResponse(row.getId(), row.getUsername(), row.getRole());
    }
}
