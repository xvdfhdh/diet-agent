package com.diet.service.agentic;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class AgentV2EvaluationSetTest {
    @Test
    void containsThirtyWellFormedRealTasks() throws Exception {
        try (InputStream input = getClass().getResourceAsStream("/agent-v2-evaluation-set.json")) {
            JsonNode tasks = new ObjectMapper().readTree(input);
            assertThat(tasks.isArray()).isTrue();
            assertThat(tasks.size()).isGreaterThanOrEqualTo(30);
            Set<String> ids = new HashSet<>();
            for (JsonNode task : tasks) {
                assertThat(task.path("id").asText()).isNotBlank();
                assertThat(ids.add(task.path("id").asText())).isTrue();
                assertThat(task.path("input").asText()).isNotBlank();
                assertThat(task.path("task").asText()).isIn(
                        "RECOMMEND", "PLAN", "SHOPPING", "CHECKIN", "FAVORITE", "PREFERENCE", "GENERAL");
                assertThat(task.path("expected").asText()).isNotBlank();
                assertThat(task.has("confirm")).isTrue();
            }
        }
    }
}
