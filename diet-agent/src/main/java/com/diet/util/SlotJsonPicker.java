package com.diet.util;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 从 LLM 返回的 JSON 槽位中提取标准标签。
 */
public class SlotJsonPicker {
    private SlotJsonPicker() {}

    public static List<String> pick(JsonNode root, String field, Map<String, List<String>> options) {
        List<String> allowed = options.getOrDefault(field, List.of());
        JsonNode node = root.path(field);
        List<String> result = new ArrayList<>();
        if (node.isArray()) {
            node.forEach(item -> addIfAllowed(result, item.asText(), allowed));
        } else if (node.isTextual()) {
            addIfAllowed(result, node.asText(), allowed);
        }
        return result;
    }

    private static void addIfAllowed(List<String> result, String value, List<String> allowed) {
        if (value == null) {
            return;
        }
        for (String candidate : value.split("\\s*[,，、;；/|]\\s*")) {
            String normalized = candidate.trim();
            if (allowed.contains(normalized) && !result.contains(normalized)) {
                result.add(normalized);
            }
        }
    }
}