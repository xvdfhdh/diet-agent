package com.diet.model;

import com.fasterxml.jackson.annotation.JsonAutoDetect;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
@Accessors(fluent = true)
@AllArgsConstructor
@NoArgsConstructor
public class CreateSessionResponse {
    private String sessionId;
}