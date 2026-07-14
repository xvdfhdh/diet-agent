package com.diet.util;

import com.diet.exception.DietException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

@Service
public class LlmJsonService {
    private final ObjectMapper objectMapper;

    public LlmJsonService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public JsonNode parseObject(String content) {
        return parse(content, '{', '}');
    }

    public JsonNode parseArray(String content) {
        return parse(content, '[', ']');
    }

    private JsonNode parse(String content, char startChar, char endChar) {
        if (content == null || content.isBlank()) {
            throw new DietException("LLM JSON 内容为空");
        }
        String cleaned = content.strip()
                .replace("```json", "")
                .replace("```", "")
                .strip();
        int start = cleaned.indexOf(startChar);
        int end = cleaned.lastIndexOf(endChar);
        if (start < 0 || end < start) {
            throw new DietException("LLM 未返回合法 JSON: " + content);
        }
        String json = cleaned.substring(start, end + 1);
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            throw new DietException("LLM JSON 解析失败: " + json, e);
        }
    }
}