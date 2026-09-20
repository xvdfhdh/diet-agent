package com.diet.service.agentic;

import com.diet.agent.loader.PromptLoader;
import com.diet.enums.RecommendationMode;
import com.diet.exception.DietException;
import com.diet.model.*;
import com.diet.service.meal.MealService;
import com.diet.service.model.ModelConfigService;
import com.diet.service.risk.RiskGuardService;
import com.diet.service.session.SessionService;
import com.diet.service.session.SessionStateService;
import com.diet.service.slot.SlotOptionService;
import com.diet.service.trace.AgentTraceService;
import com.diet.util.JsonService;
import com.diet.util.LlmJsonService;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.memory.InMemoryMemory;
import io.agentscope.core.message.Msg;
import io.agentscope.core.tool.Toolkit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

@Service
public class AgenticRecommendationService {
    private final ModelConfigService modelConfigService;
    private final PromptLoader promptLoader;
    private final DietAgentToolsFactory toolsFactory;
    private final SessionStateService sessionStateService;
    private final SessionService sessionService;
    private final MealService mealService;
    private final SlotOptionService slotOptionService;
    private final RiskGuardService riskGuardService;
    private final AgentResultCommitService commitService;
    private final AgentMutationPolicy mutationPolicy;
    private final AgentTraceService traceService;
    private final LlmJsonService llmJsonService;
    private final JsonService jsonService;
    private final Duration timeout;

    public AgenticRecommendationService(ModelConfigService modelConfigService, PromptLoader promptLoader,
                                         DietAgentToolsFactory toolsFactory, SessionStateService sessionStateService,
                                         SessionService sessionService, MealService mealService,
                                         SlotOptionService slotOptionService, RiskGuardService riskGuardService,
                                         AgentResultCommitService commitService, AgentMutationPolicy mutationPolicy,
                                         AgentTraceService traceService, LlmJsonService llmJsonService,
                                         JsonService jsonService,
                                         @Value("${diet.agentic.timeout-seconds:20}") long timeoutSeconds) {
        this.modelConfigService = modelConfigService;
        this.promptLoader = promptLoader;
        this.toolsFactory = toolsFactory;
        this.sessionStateService = sessionStateService;
        this.sessionService = sessionService;
        this.mealService = mealService;
        this.slotOptionService = slotOptionService;
        this.riskGuardService = riskGuardService;
        this.commitService = commitService;
        this.mutationPolicy = mutationPolicy;
        this.traceService = traceService;
        this.llmJsonService = llmJsonService;
        this.jsonService = jsonService;
        this.timeout = Duration.ofSeconds(Math.max(5, Math.min(timeoutSeconds, 60)));
    }

    public ChatResponse execute(Long userId, ChatRequest request, Consumer<String> progress,
                                Consumer<AgentActivity> activityConsumer) {
        SessionState state = sessionStateService.loadOrCreate(request.sessionId(), userId, request.sourceMode());
        String traceId = "trace_" + UUID.randomUUID().toString().replace("-", "");
        AgentRunContext context = new AgentRunContext(userId, state.sourceMode(), request.message(), activityConsumer);
        try (AgentTraceService.TraceScope ignored = traceService.openTrace(
                traceId, state.sessionId(), userId, RecommendationMode.AGENT)) {
            try {
                traceService.recordEvent("AGENT_STARTED", "AGENTIC", request.message(),
                        java.util.Map.of("sourceMode", state.sourceMode(), "time", LocalDateTime.now()));
                progress.accept("智能 Agent 正在判断需要调用哪些工具…");
                Toolkit toolkit = new Toolkit();
                toolkit.registerTool(toolsFactory.create(context));
                ReActAgent agent = ReActAgent.builder()
                        .name("diet_agentic_recommender")
                        .model(modelConfigService.mainModel())
                        .sysPrompt(promptLoader.load("diet/prompts/agentic-recommendation.txt"))
                        .toolkit(toolkit)
                        .memory(new InMemoryMemory())
                        .maxIters(6)
                        .build();
                Msg response;
                try {
                    response = traceService.callAgent(state.sessionId(), "AgenticRecommendationAgent",
                            modelConfigService.current().mainModel(), agent, buildPrompt(userId, request, state), timeout);
                } catch (RuntimeException error) {
                    recordActivities(context.activities());
                    throw error;
                }
                recordActivities(context.activities());
                if (context.limitExceeded()) throw new DietException("工具调用次数超过限制");
                if (context.toolCallCount() == 0) throw failure("TOOL_CALL_UNSUPPORTED", "模型没有调用任何工具", null, context);

                AgentRecommendationResult result = parseResult(response == null ? null : response.getTextContent(), context);
                List<MealResponse> displayMeals = validateAndHydrate(result, state, context);
                AgentRecommendationResult guarded = guard(request.message(), result, displayMeals, context);
                if (guarded != result) displayMeals = List.of();
                ChatResponse chatResponse = commitService.commit(userId, request.message(), traceId, state,
                        guarded, displayMeals, context.stagedMutations());
                if (!context.stagedMutations().isEmpty()) {
                    traceService.recordEvent("ACTION_COMMITTED", "AGENTIC", null,
                            java.util.Map.of("count", context.stagedMutations().size()));
                }
                traceService.markExecution(RecommendationMode.AGENT, null, context.toolCallCount());
                return chatResponse.withExecution(new ChatExecution(RecommendationMode.AGENT, RecommendationMode.AGENT,
                        false, null, context.activities(), !context.stagedMutations().isEmpty()));
            } catch (AgentRunException error) {
                traceService.markExecution(RecommendationMode.STANDARD, error.code(), error.toolCallCount());
                traceService.recordEvent("AGENT_FALLBACK", "AGENTIC", request.message(),
                        java.util.Map.of("code", error.code(), "message", error.getMessage()));
                throw error;
            } catch (Exception error) {
                String code = classify(error);
                traceService.markExecution(RecommendationMode.STANDARD, code, context.toolCallCount());
                traceService.recordEvent("AGENT_FALLBACK", "AGENTIC", request.message(),
                        java.util.Map.of("code", code, "message",
                                error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage()));
                throw failure(code, "智能推荐执行失败", error, context);
            }
        }
    }

    private String buildPrompt(Long userId, ChatRequest request, SessionState state) {
        return """
                当前时间：%s
                当前数据源：%s（必须严格遵守，不得跨库）
                当前会话槽位：%s
                最近对话：%s
                当前合法槽位：%s
                用户原话：%s
                请按系统要求调用工具并只返回最终 JSON。
                """.formatted(LocalDateTime.now(), state.sourceMode(), state.slots(),
                sessionService.recentConversationTurns(state.sessionId(), userId, 6),
                slotOptionService.findAllOptions(), request.message());
    }

    private AgentRecommendationResult parseResult(String content, AgentRunContext context) {
        try {
            AgentRecommendationResult parsed = jsonService.fromJson(
                    llmJsonService.parseObject(content).toString(), AgentRecommendationResult.class);
            if (parsed == null) throw new IllegalArgumentException("Agent 输出为空");
            return new AgentRecommendationResult(parsed.responseType(), parsed.speechText(), parsed.mealIds(),
                    normalize(parsed.resolvedSlots()), parsed.nextAction(), parsed.clarifyQuestion(), parsed.missingSlots());
        } catch (Exception error) {
            throw failure("INVALID_OUTPUT", "Agent 最终输出不是合法 JSON", error, context);
        }
    }

    private List<MealResponse> validateAndHydrate(AgentRecommendationResult result, SessionState state,
                                                   AgentRunContext context) {
        if (result == null || result.speechText() == null || result.speechText().isBlank())
            throw failure("INVALID_OUTPUT", "Agent 回复文本为空", null, context);
        SlotBundle slots = normalize(result.resolvedSlots());
        slotOptionService.validate(slots);
        List<Long> ids = result.mealIds().stream().filter(java.util.Objects::nonNull).distinct().limit(3).toList();
        if ("CLARIFY".equalsIgnoreCase(result.responseType())) {
            if (!context.stagedMutations().isEmpty()) throw failure("POLICY_VIOLATION", "澄清回复不能提交操作", null, context);
            return List.of();
        }
        List<MealResponse> values = new ArrayList<>();
        for (Long id : ids) {
            if (!context.wasRetrieved(id)) throw failure("UNVERIFIED_MEAL_ID", "Agent 返回了未检索的餐食", null, context);
            MealItem meal = mealService.findAccessibleMeal(state.userId(), id);
            if (meal == null || meal.sourceType() != state.sourceMode())
                throw failure("UNVERIFIED_MEAL_ID", "餐食不存在、越权或跨库", null, context);
            values.add(MealResponse.from(meal));
        }
        return List.copyOf(values);
    }

    private AgentRecommendationResult guard(String input, AgentRecommendationResult result,
                                              List<MealResponse> meals, AgentRunContext context) {
        ResponseResult response = new ResponseResult(result.speechText(), meals,
                result.nextAction() == null ? "WAIT_USER" : result.nextAction());
        RiskGuardResult guard = riskGuardService.check(input, null, RecommendResult.empty(), response);
        if (guard.passed()) return result;
        if (!context.stagedMutations().isEmpty())
            throw failure("POLICY_VIOLATION", "健康风险回复不能执行写操作", null, context);
        return new AgentRecommendationResult("ANSWER", guard.rewriteSuggestion(), List.of(), SlotBundle.empty(),
                "WAIT_USER", null, List.of());
    }

    private SlotBundle normalize(SlotBundle value) {
        if (value == null) return SlotBundle.empty();
        return new SlotBundle(value.mealTime(), value.mood(), value.scene(), value.healthGoal(),
                value.cuisine(), value.taste(), value.convenience());
    }

    private void recordActivities(List<AgentActivity> activities) {
        for (AgentActivity activity : activities) {
            traceService.recordEvent("TOOL_CALLED", "TOOL", activity.name(), null);
            traceService.recordEvent("STAGED".equals(activity.status()) ? "ACTION_STAGED" : "TOOL_RESULT",
                    "TOOL", activity.name(), activity.detail());
        }
    }

    private String classify(Exception error) {
        String text = (error.getClass().getName() + " " + error.getMessage()).toLowerCase(java.util.Locale.ROOT);
        if (text.contains("timeout")) return "AGENT_TIMEOUT";
        if (text.contains("tool") || text.contains("工具")) return "TOOL_ERROR";
        if (text.contains("json") || text.contains("parse")) return "INVALID_OUTPUT";
        return "MODEL_ERROR";
    }

    private AgentRunException failure(String code, String message, Throwable cause, AgentRunContext context) {
        return new AgentRunException(code, message, cause, context.activities(), context.toolCallCount(),
                mutationPolicy.mutationRequested(context.userInput()));
    }
}
