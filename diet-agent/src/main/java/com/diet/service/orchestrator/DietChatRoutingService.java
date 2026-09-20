package com.diet.service.orchestrator;

import com.diet.enums.RecommendationMode;
import com.diet.exception.DietException;
import com.diet.model.AgentActivity;
import com.diet.model.ChatExecution;
import com.diet.model.ChatRequest;
import com.diet.model.ChatResponse;
import com.diet.service.agentic.AgentCircuitBreaker;
import com.diet.service.agentic.AgentMutationPolicy;
import com.diet.service.agentic.AgentRunException;
import com.diet.service.agentic.AgenticRecommendationService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/** Chooses the stable or tool-using recommendation pipeline and owns fallback semantics. */
@Service
public class DietChatRoutingService {
    private static final Set<String> INFRASTRUCTURE_FAILURES = Set.of(
            "MODEL_ERROR", "AGENT_TIMEOUT", "TOOL_ERROR", "TOOL_CALL_UNSUPPORTED");

    private final DietOrchestratorService standardService;
    private final AgenticRecommendationService agenticService;
    private final AgentCircuitBreaker circuitBreaker;
    private final AgentMutationPolicy mutationPolicy;
    private final ConcurrentHashMap<String, Object> sessionLocks = new ConcurrentHashMap<>();

    public DietChatRoutingService(DietOrchestratorService standardService,
                                  AgenticRecommendationService agenticService,
                                  AgentCircuitBreaker circuitBreaker,
                                  AgentMutationPolicy mutationPolicy) {
        this.standardService = standardService;
        this.agenticService = agenticService;
        this.circuitBreaker = circuitBreaker;
        this.mutationPolicy = mutationPolicy;
    }

    public ChatResponse dietChat(Long userId, ChatRequest request) {
        return dietChat(userId, request, ignored -> {}, ignored -> {});
    }

    public ChatResponse dietChat(Long userId, ChatRequest request, Consumer<String> progress,
                                 Consumer<AgentActivity> activities) {
        if (request == null || request.message() == null || request.message().isBlank()) {
            throw new DietException("用户问题不能为空");
        }
        if (request.sourceMode() == null) {
            throw new DietException("sourceMode 不能为空，请选择 PERSONAL 或 PUBLIC");
        }
        Consumer<String> safeProgress = progress == null ? ignored -> {} : progress;
        Consumer<AgentActivity> safeActivities = activities == null ? ignored -> {} : activities;
        RecommendationMode requested = request.recommendationMode() == null
                ? RecommendationMode.STANDARD : request.recommendationMode();
        if (requested == RecommendationMode.STANDARD) {
            return standardService.dietChat(userId, request, safeProgress)
                    .withExecution(ChatExecution.standard(RecommendationMode.STANDARD));
        }

        String lockKey = userId + "::" + (request.sessionId() == null ? "new" : request.sessionId());
        synchronized (sessionLocks.computeIfAbsent(lockKey, ignored -> new Object())) {
            if (circuitBreaker.isOpen()) {
                safeProgress.accept("智能模式暂时熔断，已切换稳定推荐…");
                return fallback(userId, request, safeProgress, "AGENT_CIRCUIT_OPEN", List.of(),
                        mutationPolicy.mutationRequested(request.message()));
            }
            try {
                ChatResponse response = agenticService.execute(userId, request, safeProgress, safeActivities);
                circuitBreaker.recordSuccess();
                return response;
            } catch (AgentRunException error) {
                if (INFRASTRUCTURE_FAILURES.contains(error.code())) {
                    circuitBreaker.recordInfrastructureFailure();
                }
                safeProgress.accept("智能链路暂不可用，正在切换稳定推荐…");
                return fallback(userId, request, safeProgress, error.code(), error.activities(),
                        error.mutationRequested());
            }
        }
    }

    private ChatResponse fallback(Long userId, ChatRequest request, Consumer<String> progress, String code,
                                  List<AgentActivity> priorActivities, boolean mutationRequested) {
        List<AgentActivity> allActivities = new ArrayList<>(priorActivities);
        if (mutationRequested) {
            allActivities.add(new AgentActivity("计划或购物清单操作", "NOT_COMMITTED",
                    "智能链路未完成，本次写操作没有执行"));
        }
        ChatResponse response = standardService.dietChat(userId, request, progress);
        return response.withExecution(new ChatExecution(RecommendationMode.AGENT, RecommendationMode.STANDARD,
                true, code, allActivities, false));
    }
}
