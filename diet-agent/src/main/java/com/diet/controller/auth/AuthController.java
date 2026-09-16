package com.diet.controller.auth;

import com.diet.constants.DietConstants;
import com.diet.model.AuthCredentials;
import com.diet.model.AuthResponse;
import com.diet.model.AuthUserResponse;
import com.diet.service.auth.AuthService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/diet/auth")
public class AuthController {
    private final AuthService service;

    public AuthController(AuthService service) {
        this.service = service;
    }

    @PostMapping("/register")
    public AuthResponse register(@RequestBody AuthCredentials credentials) {
        return service.register(credentials);
    }

    @PostMapping("/login")
    public AuthResponse login(@RequestBody AuthCredentials credentials) {
        return service.login(credentials);
    }

    @GetMapping("/me")
    public AuthUserResponse me(@RequestAttribute(DietConstants.AUTH_USER_ID) Long userId) {
        return service.me(userId);
    }

    @PostMapping("/logout")
    public void logout(@RequestAttribute(DietConstants.AUTH_USER_ID) Long userId) {
        service.logout(userId);
    }
}
