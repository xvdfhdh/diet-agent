package com.diet.model;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ChatStreamEvent(String type, String text, ChatResponse response) {
    public static ChatStreamEvent status(String text) {
        return new ChatStreamEvent("status", text, null);
    }

    public static ChatStreamEvent delta(String text) {
        return new ChatStreamEvent("delta", text, null);
    }

    public static ChatStreamEvent complete(ChatResponse response) {
        return new ChatStreamEvent("complete", null, response);
    }

    public static ChatStreamEvent error(String text) {
        return new ChatStreamEvent("error", text, null);
    }
}
