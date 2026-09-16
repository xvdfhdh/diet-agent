package com.diet.model;

public record AuthResponse(String token, AuthUserResponse user) {
}
