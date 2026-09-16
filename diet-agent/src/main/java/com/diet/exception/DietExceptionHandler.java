package com.diet.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

@RestControllerAdvice(basePackages = "com.diet")
public class DietExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(DietExceptionHandler.class);

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleStatusException(ResponseStatusException error) {
        return ResponseEntity.status(error.getStatusCode())
                .body(Map.of("message", error.getReason() == null ? "请求失败" : error.getReason()));
    }
    @ExceptionHandler(DietException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> handleNewDietException(DietException e) {
        return Map.of("message", e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Map<String, String> handleException(Exception e) {
        log.error("未处理的接口异常", e);
        return Map.of("message", "服务异常，请联系管理员查看后端日志");
    }
}
