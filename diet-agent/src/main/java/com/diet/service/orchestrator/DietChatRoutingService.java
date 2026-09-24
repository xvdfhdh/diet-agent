package com.diet.service.orchestrator;

import com.diet.enums.RecommendationMode;
import com.diet.enums.AgentTaskType;
import com.diet.enums.SourceStrategy;
import com.diet.exception.DietException;
import com.diet.model.AgentActivity;
import com.diet.model.ChatExecution;
import com.diet.model.ChatRequest;
import com.diet.model.ChatResponse;
import com.diet.service.agentic.AgentCircuitBreaker;
import com.diet.service.agentic.AgentMutationPolicy;
import com.diet.service.agentic.AgentRunException;
import com.diet.service.agentic.AgenticRecommendationService;
import com.diet.service.agentic.PendingAgentActionService;
import com.diet.model.PendingAgentActionRow;
import com.diet.model.AgentActionResponse;
import com.diet.service.session.SessionService;
import com.diet.service.trace.AgentTraceService;
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
    private final PendingAgentActionService pendingActions;
    private final SessionService sessions;
    private final AgentTraceService traces;
    private final ConcurrentHashMap<String, Object> sessionLocks = new ConcurrentHashMap<>();

    public DietChatRoutingService(DietOrchestratorService standardService,
                                  AgenticRecommendationService agenticService,
                                  AgentCircuitBreaker circuitBreaker,
                                  AgentMutationPolicy mutationPolicy,
                                  PendingAgentActionService pendingActions,
                                  SessionService sessions,
                                  AgentTraceService traces) {
        this.standardService = standardService;
        this.agenticService = agenticService;
        this.circuitBreaker = circuitBreaker;
        this.mutationPolicy = mutationPolicy;
        this.pendingActions = pendingActions;
        this.sessions = sessions;
        this.traces = traces;
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

        String effectiveSessionId = request.sessionId() == null || request.sessionId().isBlank()
                ? "sess_" + java.util.UUID.randomUUID().toString().replace("-", "") : request.sessionId();
        ChatRequest routedRequest = new ChatRequest(effectiveSessionId, request.message(), request.sourceMode(),
                RecommendationMode.AGENT, request.sourceStrategy(), request.context());
        String traceId = "trace_" + java.util.UUID.randomUUID().toString().replace("-", "");
        try (AgentTraceService.TraceScope ignored = traces.openTrace(
                traceId, effectiveSessionId, userId, RecommendationMode.AGENT)) {
            traces.recordEvent("REQUEST_RECEIVED", "ROUTING", routedRequest,
                    java.util.Map.of("requestedMode", "AGENT"));
            ChatResponse pendingResponse = handlePendingActionReply(userId, routedRequest);
            if (pendingResponse != null) return pendingResponse;

            String lockKey = userId + "::" + effectiveSessionId;
            synchronized (sessionLocks.computeIfAbsent(lockKey, key -> new Object())) {
                if (circuitBreaker.isOpen()) {
                    safeProgress.accept("智能模式暂时熔断，已切换稳定推荐…");
                    return fallback(userId, routedRequest, safeProgress, "AGENT_CIRCUIT_OPEN", List.of(),
                            mutationPolicy.mutationRequested(routedRequest.message()));
                }
                try {
                    ChatResponse response = agenticService.execute(userId, routedRequest, safeProgress, safeActivities);
                    circuitBreaker.recordSuccess();
                    return response;
                } catch (AgentRunException error) {
                    if (INFRASTRUCTURE_FAILURES.contains(error.code())) {
                        circuitBreaker.recordInfrastructureFailure();
                    }
                    safeProgress.accept("智能链路暂不可用，正在切换稳定推荐…");
                    return fallback(userId, routedRequest, safeProgress, error.code(), error.activities(),
                            error.mutationRequested());
                }
            }
        }
    }

    private ChatResponse fallback(Long userId, ChatRequest request, Consumer<String> progress, String code,
                                  List<AgentActivity> priorActivities, boolean mutationRequested) {
        traces.markFallback(code);
        List<AgentActivity> allActivities = new ArrayList<>(priorActivities);
        if (mutationRequested) {
            allActivities.add(new AgentActivity("计划或购物清单操作", "NOT_COMMITTED",
                    "智能链路未完成，本次写操作没有执行"));
        }
        ChatResponse response = standardService.dietChat(userId, request, progress);
        return response.withExecution(new ChatExecution(RecommendationMode.AGENT, RecommendationMode.STANDARD,
                true, code, allActivities, false, null,
                request.sourceStrategy() == null ? SourceStrategy.SELECTED_ONLY : request.sourceStrategy(), 0));
    }

    private ChatResponse handlePendingActionReply(Long userId, ChatRequest request) {
        String text = request.message().trim().replace("。", "");
        boolean confirm = Set.of("确认", "确认执行", "执行", "可以", "好的，执行").contains(text);
        boolean cancel = Set.of("取消", "不要执行", "不执行", "算了").contains(text);
        if (!confirm && !cancel) return null;
        PendingAgentActionRow pending = pendingActions.latest(userId, request.sessionId());
        if (pending == null) return null;
        AgentActionResponse result = confirm ? pendingActions.confirm(userId, pending.getId())
                : pendingActions.cancel(userId, pending.getId());
        traces.markExecution(RecommendationMode.AGENT, null, 0);
        String traceId = traces.activeTraceId("trace_action_" + java.util.UUID.randomUUID().toString().replace("-", ""));
        sessions.appendMessage(request.sessionId(), "user", request.message(), null, traceId);
        sessions.appendMessage(request.sessionId(), "assistant", result.message(), pending.getTaskType(), traceId);
        AgentTaskType taskType;
        try { taskType = AgentTaskType.valueOf(pending.getTaskType()); }
        catch (Exception ignored) { taskType = AgentTaskType.GENERAL; }
        return ChatResponse.answer(request.sessionId(), traceId, result.message(), List.of(), "WAIT_USER")
                .withExecution(new ChatExecution(RecommendationMode.AGENT, RecommendationMode.AGENT,
                        false, null, List.of(new AgentActivity(confirm ? "确认执行" : "取消操作", "COMPLETED",
                        result.message())), confirm, taskType,
                        request.sourceStrategy() == null ? SourceStrategy.UNIFIED : request.sourceStrategy(), 0));
    }
}
