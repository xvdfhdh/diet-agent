package com.diet.service.orchestrator;

import com.diet.enums.RecommendationMode;
import com.diet.enums.SourceMode;
import com.diet.model.AgentActivity;
import com.diet.model.ChatRequest;
import com.diet.model.ChatResponse;
import com.diet.service.agentic.AgentCircuitBreaker;
import com.diet.service.agentic.AgentMutationPolicy;
import com.diet.service.agentic.AgentRunException;
import com.diet.service.agentic.AgenticRecommendationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class DietChatRoutingServiceTest {
    private DietOrchestratorService standard;
    private AgenticRecommendationService agentic;
    private AgentCircuitBreaker breaker;
    private DietChatRoutingService routing;

    @BeforeEach
    void setUp() {
        standard = mock(DietOrchestratorService.class);
        agentic = mock(AgenticRecommendationService.class);
        breaker = new AgentCircuitBreaker();
        routing = new DietChatRoutingService(standard, agentic, breaker, new AgentMutationPolicy());
    }

    @Test
    void defaultsToStablePipeline() {
        ChatRequest request = request(null, "推荐午饭");
        when(standard.dietChat(eq(7L), eq(request), any())).thenReturn(answer());

        ChatResponse response = routing.dietChat(7L, request);

        assertThat(response.execution().requestedMode()).isEqualTo(RecommendationMode.STANDARD);
        assertThat(response.execution().actualMode()).isEqualTo(RecommendationMode.STANDARD);
        verifyNoInteractions(agentic);
    }

    @Test
    void fallsBackOnceAndDoesNotCommitRequestedMutation() {
        ChatRequest request = request(RecommendationMode.AGENT, "推荐一道菜并加入明天午餐计划");
        AgentRunException failure = new AgentRunException("AGENT_TIMEOUT", "timeout", null,
                List.of(AgentActivity.completed("读取长期偏好", "完成")), 1, true);
        doThrow(failure).when(agentic).execute(eq(7L), eq(request), any(), any());
        when(standard.dietChat(eq(7L), eq(request), any())).thenReturn(answer());

        ChatResponse response = routing.dietChat(7L, request);

        assertThat(response.execution().fallbackOccurred()).isTrue();
        assertThat(response.execution().actualMode()).isEqualTo(RecommendationMode.STANDARD);
        assertThat(response.execution().fallbackCode()).isEqualTo("AGENT_TIMEOUT");
        assertThat(response.execution().mutationsCommitted()).isFalse();
        assertThat(response.execution().activities()).anyMatch(it -> "NOT_COMMITTED".equals(it.status()));
        verify(standard, times(1)).dietChat(eq(7L), eq(request), any());
    }

    @Test
    void opensCircuitAfterThreeInfrastructureFailuresAndResetsOnSuccess() {
        breaker.recordInfrastructureFailure();
        breaker.recordInfrastructureFailure();
        assertThat(breaker.isOpen()).isFalse();
        breaker.recordInfrastructureFailure();
        assertThat(breaker.isOpen()).isTrue();
        breaker.recordSuccess();
        assertThat(breaker.isOpen()).isFalse();
    }

    private ChatRequest request(RecommendationMode mode, String message) {
        return new ChatRequest("session-1", message, SourceMode.PUBLIC, mode, java.util.Map.of());
    }

    private ChatResponse answer() {
        return ChatResponse.answer("session-1", "trace-1", "可以", List.of(), "WAIT_USER");
    }
}
