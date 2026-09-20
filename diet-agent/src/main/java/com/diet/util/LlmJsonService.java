package com.diet.util;

import com.diet.exception.DietException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.json.JsonReadFeature;
import org.springframework.stereotype.Service;

@Service
public class LlmJsonService {
    private final ObjectMapper objectMapper;

    public LlmJsonService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper.copy()
                .enable(JsonReadFeature.ALLOW_SINGLE_QUOTES.mappedFeature())
                .enable(JsonReadFeature.ALLOW_TRAILING_COMMA.mappedFeature())
                .enable(JsonReadFeature.ALLOW_UNQUOTED_FIELD_NAMES.mappedFeature())
                .enable(JsonReadFeature.ALLOW_JAVA_COMMENTS.mappedFeature())
                .enable(JsonReadFeature.ALLOW_UNESCAPED_CONTROL_CHARS.mappedFeature());
    }

    public JsonNode parseObject(String content) {
        return parse(content, '{', '}');
    }

    public JsonNode parseArray(String content) {
        return parse(content, '[', ']');
    }

    /** 接受对象或数组，供不同模型返回包裹对象或直接数组时复用。 */
    public JsonNode parseValue(String content) {
        if (content == null || content.isBlank()) throw new DietException("LLM JSON 内容为空");
        String cleaned = clean(content);
        int objectStart = cleaned.indexOf('{');
        int arrayStart = cleaned.indexOf('[');
        int start;
        if (objectStart < 0) start = arrayStart;
        else if (arrayStart < 0) start = objectStart;
        else start = Math.min(objectStart, arrayStart);
        if (start < 0) throw new DietException("LLM 未返回合法 JSON: " + abbreviate(content));
        String json = extractBalanced(cleaned, start);
        try {
            return objectMapper.readTree(json);
        } catch (Exception error) {
            throw new DietException("LLM JSON 解析失败: " + abbreviate(json), error);
        }
    }

    private JsonNode parse(String content, char startChar, char endChar) {
        if (content == null || content.isBlank()) {
            throw new DietException("LLM JSON 内容为空");
        }
        String cleaned = clean(content);
        int start = cleaned.indexOf(startChar);
        if (start < 0) throw new DietException("LLM 未返回合法 JSON: " + abbreviate(content));
        String json = extractBalanced(cleaned, start);
        try {
            JsonNode value = objectMapper.readTree(json);
            if (startChar == '{' && !value.isObject() || startChar == '[' && !value.isArray()) {
                throw new DietException("LLM 返回的 JSON 类型不正确");
            }
            return value;
        } catch (Exception e) {
            if (e instanceof DietException dietException) throw dietException;
            throw new DietException("LLM JSON 解析失败: " + abbreviate(json), e);
        }
    }

    private String clean(String content) {
        return content.replace("\uFEFF", "").replaceAll("(?i)```(?:json)?", "").strip();
    }

    private String extractBalanced(String value, int start) {
        char opening = value.charAt(start);
        char closing = opening == '{' ? '}' : ']';
        int depth = 0;
        char quote = 0;
        boolean escaped = false;
        for (int index = start; index < value.length(); index++) {
            char current = value.charAt(index);
            if (quote != 0) {
                if (escaped) escaped = false;
                else if (current == '\\') escaped = true;
                else if (current == quote) quote = 0;
                continue;
            }
            if (current == '"' || current == '\'') { quote = current; continue; }
            if (current == opening) depth++;
            else if (current == closing && --depth == 0) return value.substring(start, index + 1);
        }
        throw new DietException("LLM JSON 括号不完整: " + abbreviate(value.substring(start)));
    }

    private String abbreviate(String value) {
        return value.length() <= 600 ? value : value.substring(0, 600) + "…";
    }
}
