package com.diet.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;

@Configuration
public class DietCorsConfig implements WebMvcConfigurer {
    private final String[] allowedOriginPatterns;

    public DietCorsConfig(@Value("${diet.cors.allowed-origin-patterns:${diet.cors.allowed-origins:http://localhost:[*],http://127.0.0.1:[*]}}") String allowedOriginPatterns) {
        this.allowedOriginPatterns = Arrays.stream(allowedOriginPatterns.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isBlank())
                .toArray(String[]::new);
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns(allowedOriginPatterns)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("Content-Type", "Authorization", "Accept")
                .maxAge(3600);
    }
}
