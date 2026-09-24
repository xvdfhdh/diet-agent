package com.diet.service.agentic;

import com.diet.agent.loader.PromptLoader;
import com.diet.enums.RecommendationMode;
import com.diet.enums.AgentTaskType;
import com.diet.enums.SourceStrategy;
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
    private final AgentTaskRouter taskRouter;
    private final AgentContextAssembler contextAssembler;
    private final Duration timeout;

    public AgenticRecommendationService(ModelConfigService modelConfigService, PromptLoader promptLoader,
                                         DietAgentToolsFactory toolsFactory, SessionStateService sessionStateService,
                                         SessionService sessionService, MealService mealService,
                                         SlotOptionService slotOptionService, RiskGuardService riskGuardService,
                                         AgentResultCommitService commitService, AgentMutationPolicy mutationPolicy,
                                         AgentTraceService traceService, LlmJsonService llmJsonService,
                                         JsonService jsonService, AgentTaskRouter taskRouter,
                                         AgentContextAssembler contextAssembler,
                                         @Value("${diet.agentic.timeout-seconds:30}") long timeoutSeconds) {
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
        this.taskRouter = taskRouter;
        this.contextAssembler = contextAssembler;
        this.timeout = Duration.ofSeconds(Math.max(5, Math.min(timeoutSeconds, 60)));
    }

    public ChatResponse execute(Long userId, ChatRequest request, Consumer<String> progress,
                                Consumer<AgentActivity> activityConsumer) {
        SessionState state = sessionStateService.loadOrCreate(request.sessionId(), userId, request.sourceMode());
        AgentTaskType taskType = taskRouter.route(request.message());
        SourceStrategy sourceStrategy = request.sourceStrategy() == null
                ? SourceStrategy.SELECTED_ONLY : request.sourceStrategy();
        AgentContextSnapshot contextSnapshot = contextAssembler.assemble(userId, state.sessionId(), taskType, sourceStrategy);
        String proposedTraceId = "trace_" + UUID.randomUUID().toString().replace("-", "");
        AgentRunContext context = new AgentRunContext(userId, state.sourceMode(), sourceStrategy,
                taskType, request.message(), activityConsumer);
            try (AgentTraceService.TraceScope ignored = traceService.openTrace(
                proposedTraceId, state.sessionId(), userId, RecommendationMode.AGENT)) {
            String traceId = traceService.activeTraceId(proposedTraceId);
            try {
                traceService.recordEvent("AGENT_STARTED", "AGENTIC", request.message(),
                        java.util.Map.of("sourceMode", state.sourceMode(), "sourceStrategy", sourceStrategy,
                                "taskType", taskType, "time", LocalDateTime.now()));
                context.activity(AgentActivity.completed("理解任务", taskLabel(taskType)));
                context.activity(AgentActivity.completed("组装饮食上下文", "已读取偏好、历史与本周计划"));
                progress.accept("智能 Agent 正在判断需要调用哪些工具…");
                Toolkit toolkit = new Toolkit();
                toolsFactory.register(toolkit, context, taskType);
                ReActAgent agent = ReActAgent.builder()
                        .name("diet_agentic_recommender")
                        .model(modelConfigService.mainModel())
                        .sysPrompt(promptLoader.load("diet/prompts/agentic-recommendation.txt"))
                        .toolkit(toolkit)
                        .memory(new InMemoryMemory())
                        .maxIters(8)
                        .build();
                Msg response;
                try {
                    response = traceService.callAgent(state.sessionId(), "AgenticRecommendationAgent",
                            modelConfigService.current().mainModel(), agent,
                            buildPrompt(request, state, contextSnapshot), timeout);
                } catch (RuntimeException error) {
                    recordActivities(context.activities());
                    throw error;
                }
                recordActivities(context.activities());
                if (context.limitExceeded()) throw new DietException("工具调用次数超过限制");
                if (context.toolCallCount() == 0 && taskType != AgentTaskType.GENERAL)
                    throw failure("TOOL_CALL_UNSUPPORTED", "模型没有调用任何工具", null, context);

                ParseOutcome parsed = parseResult(response == null ? null : response.getTextContent(), context);
                AgentRecommendationResult result = parsed.result();
                traceService.markAgentMetadata(taskType, sourceStrategy, parsed.repairCount());
                List<MealResponse> displayMeals = validateAndHydrate(result, state, context);
                AgentRecommendationResult guarded = guard(request.message(), result, displayMeals, context);
                if (guarded != result) displayMeals = List.of();
                ChatResponse chatResponse = commitService.commit(userId, request.message(), traceId, state,
                        guarded, displayMeals, context.stagedMutations(), sourceStrategy);
                if (!context.stagedMutations().isEmpty()) {
                    traceService.recordEvent("ACTION_COMMITTED", "AGENTIC", null,
                            java.util.Map.of("count", context.stagedMutations().size()));
                }
                traceService.markExecution(RecommendationMode.AGENT, null, context.toolCallCount());
                return chatResponse.withExecution(new ChatExecution(RecommendationMode.AGENT, RecommendationMode.AGENT,
                        false, null, context.activities(), !context.stagedMutations().isEmpty()
                        && chatResponse.actionPreview() == null, taskType, sourceStrategy, parsed.repairCount()));
            } catch (AgentRunException error) {
                traceService.markAgentMetadata(taskType, sourceStrategy, 0);
                traceService.markExecution(RecommendationMode.STANDARD, error.code(), error.toolCallCount());
                traceService.recordEvent("AGENT_FALLBACK", "AGENTIC", request.message(),
                        java.util.Map.of("code", error.code(), "message", error.getMessage()));
                throw error;
            } catch (Exception error) {
                traceService.markAgentMetadata(taskType, sourceStrategy, 0);
                String code = classify(error);
                traceService.markExecution(RecommendationMode.STANDARD, code, context.toolCallCount());
                traceService.recordEvent("AGENT_FALLBACK", "AGENTIC", request.message(),
                        java.util.Map.of("code", code, "message",
                                error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage()));
                throw failure(code, "智能推荐执行失败", error, context);
            }
        }
    }

    private String buildPrompt(ChatRequest request, SessionState state, AgentContextSnapshot context) {
        return """
                当前时间：%s
                当前界面数据源：%s（仅 SELECTED_ONLY 时严格遵守）
                数据源策略：%s（UNIFIED 表示可同时使用当前用户个人库和公共库，优先个人库）
                当前任务类型：%s
                当前会话槽位：%s
                自动组装的可信上下文：%s
                当前合法槽位：%s
                用户原话：%s
                请按系统要求调用工具并只返回最终 JSON。
                """.formatted(LocalDateTime.now(), state.sourceMode(), context.sourceStrategy(), context.taskType(), state.slots(),
                jsonService.toJson(context),
                slotOptionService.findAllOptions(), request.message());
    }

    private ParseOutcome parseResult(String content, AgentRunContext context) {
        try {
            return new ParseOutcome(parseResultValue(content), 0);
        } catch (Exception firstError) {
            try {
                String repaired = repairOutput(content);
                return new ParseOutcome(parseResultValue(repaired), 1);
            } catch (Exception repairError) {
                repairError.addSuppressed(firstError);
                throw failure("INVALID_OUTPUT", "Agent 最终输出不是合法 JSON", repairError, context);
            }
        }
    }

    private AgentRecommendationResult parseResultValue(String content) {
        AgentRecommendationResult parsed = jsonService.fromJson(
                llmJsonService.parseObject(content).toString(), AgentRecommendationResult.class);
        if (parsed == null) throw new IllegalArgumentException("Agent 输出为空");
        return new AgentRecommendationResult(parsed.responseType(), parsed.speechText(), parsed.mealIds(),
                normalize(parsed.resolvedSlots()), parsed.nextAction(), parsed.clarifyQuestion(), parsed.missingSlots());
    }

    private String repairOutput(String content) {
        ReActAgent repair = ReActAgent.builder().name("diet_agent_output_repair")
                .model(modelConfigService.mainModel())
                .sysPrompt("把输入修复成符合字段 responseType,speechText,mealIds,resolvedSlots,nextAction,clarifyQuestion,missingSlots 的单个 JSON 对象；不得增加事实，只输出 JSON。")
                .memory(new InMemoryMemory()).maxIters(2).build();
        Msg response = repair.call(Msg.builder().role(io.agentscope.core.message.MsgRole.USER)
                .textContent(content == null ? "" : content).build()).block(Duration.ofSeconds(8));
        if (response == null || response.getTextContent() == null) throw new IllegalArgumentException("修复模型未返回内容");
        return response.getTextContent();
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
            if (meal == null || context.sourceStrategy() == SourceStrategy.SELECTED_ONLY
                    && meal.sourceType() != state.sourceMode())
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

    private String taskLabel(AgentTaskType taskType) {
        return switch (taskType) {
            case RECOMMEND -> "智能推荐"; case PLAN -> "饮食计划"; case SHOPPING -> "购物清单";
            case CHECKIN -> "饮食打卡"; case FAVORITE -> "收藏管理"; case PREFERENCE -> "偏好管理";
            case GENERAL -> "饮食问答";
        };
    }

    private record ParseOutcome(AgentRecommendationResult result, int repairCount) { }
}
