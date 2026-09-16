package com.diet.config;

import com.diet.constants.DietConstants;
import com.diet.model.DietUserRow;
import com.diet.service.auth.AuthService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.util.Map;

@Component
public class AuthInterceptor implements HandlerInterceptor {
    private final AuthService authService;
    private final ObjectMapper objectMapper;

    public AuthInterceptor(AuthService authService, ObjectMapper objectMapper) {
        this.authService = authService;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ") || authorization.length() <= 7) {
            reject(response, HttpStatus.UNAUTHORIZED, "请先登录");
            return false;
        }
        try {
            DietUserRow user = authService.authenticatedUser(authorization.substring(7).trim());
            if (handler instanceof HandlerMethod method &&
                    (method.hasMethodAnnotation(AdminOnly.class) || method.getBeanType().isAnnotationPresent(AdminOnly.class)) &&
                    !"ADMIN".equals(user.getRole())) {
                reject(response, HttpStatus.FORBIDDEN, "仅管理员可执行此操作");
                return false;
            }
            request.setAttribute(DietConstants.AUTH_USER_ID, user.getId());
            request.setAttribute(DietConstants.AUTH_ROLE, user.getRole());
            return true;
        } catch (ResponseStatusException error) {
            reject(response, HttpStatus.UNAUTHORIZED, "登录已失效，请重新登录");
            return false;
        }
    }

    private void reject(HttpServletResponse response, HttpStatus status, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType("application/json;charset=UTF-8");
        objectMapper.writeValue(response.getWriter(), Map.of("message", message));
    }
}
