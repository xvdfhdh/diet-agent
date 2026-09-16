package com.diet.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class AuthMvcConfig implements WebMvcConfigurer {
    private final AuthInterceptor interceptor;

    public AuthMvcConfig(AuthInterceptor interceptor) {
        this.interceptor = interceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(interceptor)
                .addPathPatterns("/api/v1/diet/**")
                .excludePathPatterns("/api/v1/diet/auth/login", "/api/v1/diet/auth/register");
    }
}
