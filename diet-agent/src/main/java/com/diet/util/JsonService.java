package com.diet.util;

import com.diet.exception.DietException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class JsonService {
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;

    public JsonService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String toJsonArray(List<String> values) {
        try {
            return objectMapper.writeValueAsString(values == null ? List.of() : values);
        } catch (Exception e) {
            throw new DietException("JSON 序列化失败", e);
        }
    }

    public List<String> fromJsonArray(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, STRING_LIST);
        } catch (Exception e) {
            throw new DietException("JSON 解析失败", e);
        }
    }
}




