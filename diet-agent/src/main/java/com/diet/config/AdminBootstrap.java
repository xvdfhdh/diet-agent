package com.diet.config;

import com.diet.service.auth.AuthService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class AdminBootstrap implements ApplicationRunner {
    private final AuthService authService;
    private final String username;
    private final String password;

    public AdminBootstrap(AuthService authService,
                          @Value("${diet.auth.admin-username:}") String username,
                          @Value("${diet.auth.admin-password:}") String password) {
        this.authService = authService;
        this.username = username;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        authService.verifySchema();
        authService.bootstrapAdmin(username, password);
    }
}
