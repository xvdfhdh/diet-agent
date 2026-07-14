package com.diet.service.evaluation;

import com.diet.exception.DietException;
import com.diet.mapper.FeedbackMapper;
import com.diet.model.EvaluationReport;
import com.diet.model.EvaluationJudgeResult;
import com.diet.model.EvaluationRequest;
import com.diet.model.FeedbackRow;
import com.diet.model.RequestTraceRow;
import com.diet.model.TraceEvaluationResult;
import com.diet.service.trace.AgentTraceService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class EvaluationService {
    private static final int DEFAULT_LIMIT = 1000;
    private static final Set<String> SLOT_NAMES = Set.of(
            "mealTime", "mood", "scene", "healthGoal", "cuisine", "taste", "convenience"
    );
    private static final List<String> FORBIDDEN_PHRASES = List.of("治好", "治疗", "保证", "一定能瘦", "根治", "替代医生");

    private final AgentTraceService agentTraceService;
    private final FeedbackMapper feedbackMapper;
    private final ObjectMapper objectMapper;
    private final EvaluationJudgeService evaluationJudgeService;

    public EvaluationService(
            AgentTraceService agentTraceService,
            FeedbackMapper feedbackMapper,
            ObjectMapper objectMapper,
            EvaluationJudgeService evaluationJudgeService
    ) {
        this.agentTraceService = agentTraceService;
        this.feedbackMapper = feedbackMapper;
        this.objectMapper = objectMapper;
        this.evaluationJudgeService = evaluationJudgeService;
    }

    public EvaluationReport evaluate(Long userId, EvaluationRequest request) {
        // 校验请求对象和时间范围，评估必须有合法的左闭右开时间窗口。
        if (request == null || request.startAt() == null || request.endAt() == null || !request.startAt().isBefore(request.endAt())) {
            // 时间范围缺失或 startAt >= endAt 时直接返回业务错误，避免扫描异常数据。
            throw new DietException("评估时间范围不合法");
        }
        // includeLlmJudge 为 true 时才调用 LLM as Judge，否则只跑规则分和用户反馈分。
        boolean includeJudge = Boolean.TRUE.equals(request.includeLlmJudge());
        // limit 为空时使用默认上限，避免后台一次评估拉取过多 trace。
        int limit = request.limit() == null ? DEFAULT_LIMIT : request.limit();
        // 从 diet_request_trace 按用户和时间范围读取待评估 trace。
        List<RequestTraceRow> traces = agentTraceService.findByTimeRange(userId, request.startAt(), request.endAt(), false, limit);
        // 按 trace 涉及的 sessionId 一次性读取用户反馈，减少后续逐条查询。
        Map<String, List<FeedbackRow>> feedbackBySession = loadFeedback(userId, request.startAt(), request.endAt(), traces);

        // 遍历每条 trace，结合对应 session 的反馈，生成单条 TraceEvaluationResult。
        List<TraceEvaluationResult> results = traces.stream()
                // 对单条 trace 执行规则解析、Judge 评分和总分汇总。
                .map(trace -> evaluateTrace(trace, feedbackBySession.getOrDefault(trace.getSessionId(), List.of()), includeJudge))
                // 收集成列表，后续用于区间聚合。
                .toList();

        // 将区间元信息、整体平均值和单条明细组装为最终报告。
        return new EvaluationReport(
                // 评估窗口开始时间，和请求保持一致。
                request.startAt(),
                // 评估窗口结束时间，和请求保持一致。
                request.endAt(),
                // 本次实际参与评估的 trace 数。
                traces.size(),
                // 统计至少包含一个人工标注字段的 trace 数。
                (int) traces.stream().filter(this::hasAnyLabel).count(),
                // 计算所有单条百分制总分的平均值，无法计算时为 null。
                average(results.stream().map(TraceEvaluationResult::score).toList()),
                // 计算每个指标在整个时间范围内的平均值。
                metricAverages(results),
                // 返回每条 trace 的评估明细，供后台页面展示和排查。
                results
        );
    }

    private Map<String, List<FeedbackRow>> loadFeedback(Long userId, LocalDateTime startAt, LocalDateTime endAt, List<RequestTraceRow> traces) {
        // 从 trace 列表里提取 sessionId，因为当前 recommend_feedback 还没有 traceId。
        List<String> sessionIds = traces.stream()
                // 取出每条 trace 所属的 sessionId。
                .map(RequestTraceRow::getSessionId)
                // 过滤掉异常空 sessionId，避免 MyBatis IN 条件里出现 null。
                .filter(Objects::nonNull)
                // 同一个 session 可能有多条 trace，这里去重减少查询参数。
                .distinct()
                // 收集成不可变列表传给 FeedbackMapper。
                .toList();
        // 查询时间范围内这些 session 的反馈，并按 sessionId 分组，方便单条 trace 取用。
        return feedbackMapper.findBySessions(userId, sessionIds, startAt, endAt).stream()
                // 同一个 session 下的反馈会被所有同 session trace 共享，这是当前无 traceId 反馈表的近似归因。
                .collect(Collectors.groupingBy(FeedbackRow::getSessionId));
    }

    private TraceEvaluationResult evaluateTrace(RequestTraceRow row, List<FeedbackRow> feedbacks, boolean includeJudge) {
        // 先把 trace_json 中的事件解析成评估需要的扁平快照。
        TraceSnapshot snapshot = parseTrace(row);
        // 使用 LinkedHashMap 保持指标输出顺序稳定，便于后台页面展示。
        Map<String, Double> metrics = new LinkedHashMap<>();
        // 意图准确率：需要人工 expected_intent，未标注则返回 null 不参与平均。
        metrics.put("intentAccuracy", intentAccuracy(row.getExpectedIntent(), snapshot.intent()));
        // 槽位准确率：需要人工 expected_slots，逐槽位比较。
        metrics.put("slotAccuracy", slotAccuracy(row.getExpectedSlots(), snapshot.slots()));
        // 澄清必要性准确率：需要人工 expected_clarify_action。
        metrics.put("clarifyNecessityAccuracy", clarifyAccuracy(row.getExpectedClarifyAction(), snapshot.clarifyAction()));
        // tokenCost 是原始观测值，单位为 token，不直接作为 0-1 分参与规则分。
        metrics.put("tokenCost", snapshot.tokenCost() == null ? null : snapshot.tokenCost().doubleValue());
        // tokenCostScore 是归一化后的成本分，越省 token 越接近 1。
        metrics.put("tokenCostScore", costScore(snapshot.tokenCost()));
        // latencyMs 是原始端到端耗时，单位毫秒。
        metrics.put("latencyMs", row.getDurationMs() == null ? null : row.getDurationMs().doubleValue());
        // latencyScore 是归一化后的延迟分，3 秒内满分，8 秒以上归零。
        metrics.put("latencyScore", latencyScore(row.getDurationMs()));
        // fallbackRate 单条 trace 用 1/0 表示是否 fallback，区间平均后就是 fallback 率。
        metrics.put("fallbackRate", snapshot.fallbackUsed() ? 1.0 : 0.0);
        // fallbackScore 是规则分使用的反向分，未 fallback 得 1。
        metrics.put("fallbackScore", snapshot.fallbackUsed() ? 0.0 : 1.0);
        // 安全合规由规则检查禁用表达和 Guard 结果的 Trace 状态间接判定。
        metrics.put("safetyCompliance", snapshot.safetyCompliance() ? 1.0 : 0.0);
        // 幻觉控制检查最终卡片是否来自重排候选集合。
        metrics.put("hallucinationControl", snapshot.hallucinationFree() ? 1.0 : 0.0);
        // 多轮一致性只在 MEAL_ADJUST 场景能计算，其他 trace 返回 null。
        metrics.put("multiTurnConsistency", snapshot.multiTurnConsistency());

        // includeJudge=true 时调用 EvaluationJudgeAgent，否则 Judge 维度不参与总分。
        EvaluationJudgeResult judge = includeJudge
                // Judge 输入只给 trace 摘要，不访问线上系统或额外数据。
                ? evaluationJudgeService.judge(row.getTraceId(), row.getSessionId(), buildJudgeInput(snapshot))
                // 未开启 Judge 时保持 null，后续 llmJudgeScore 也会为 null。
                : null;
        // Judge 成功返回时，把 1-5 分的解释质量和自然度放入指标明细。
        if (judge != null) {
            // 解释质量：是否说明“为什么推荐”，且理由是否贴合 trace。
            metrics.put("explanationQuality", judge.explanationQuality());
            // 自然度：表达是否简洁自然，不像机械字段拼接。
            metrics.put("naturalness", judge.naturalness());
        }

        // 用户反馈分来自 recommend_feedback；当前按 session 近似归因。
        Double userFeedbackScore = feedbackScore(feedbacks);
        // 规则分只聚合 0-1 分制的规则指标，不把原始 latency/token 值直接混入。
        Double ruleScore = groupAverage(metrics, List.of(
                // 人工标注后的意图准确率。
                "intentAccuracy",
                // 人工标注后的槽位准确率。
                "slotAccuracy",
                // 人工标注后的澄清动作准确率。
                "clarifyNecessityAccuracy",
                // token 成本归一化分。
                "tokenCostScore",
                // 延迟归一化分。
                "latencyScore",
                // fallback 反向分。
                "fallbackScore",
                // 安全合规分。
                "safetyCompliance",
                // 幻觉控制分。
                "hallucinationControl",
                // 多轮一致性分。
                "multiTurnConsistency"
        ));
        // Judge 原始分是 1-5，这里归一化成 0-1 后求平均。
        Double llmJudgeScore = judge == null ? null : average(List.of(judge.explanationQuality() / 5.0, judge.naturalness() / 5.0));
        // 按规则分 60%、Judge 分 10%、用户反馈 30% 的权重计算单条总分。
        Double score = weightedScore(ruleScore, llmJudgeScore, userFeedbackScore);

        // detail 保存预测值、标注值和 Judge 原因，方便后台点开单条 trace 排查。
        Map<String, Object> detail = new LinkedHashMap<>();
        // Trace 解析出的最终意图。
        detail.put("predictedIntent", snapshot.intent());
        // Trace 解析出的最终槽位。
        detail.put("predictedSlots", snapshot.slots());
        // Trace 解析出的澄清动作。
        detail.put("predictedClarifyAction", snapshot.clarifyAction());
        // 人工标注的期望意图。
        detail.put("expectedIntent", row.getExpectedIntent());
        // 人工标注的期望槽位 JSON。
        detail.put("expectedSlots", row.getExpectedSlots());
        // 人工标注的期望澄清动作。
        detail.put("expectedClarifyAction", row.getExpectedClarifyAction());
        // 当前 session 关联到的反馈数量。
        detail.put("feedbackCount", feedbacks.size());
        // 标记 Judge 维度是否启用，便于前端解释分数来源。
        detail.put("judgeMode", includeJudge ? "LLM_AS_JUDGE" : "DISABLED");
        // Judge 给出的简短原因，Judge 未启用或失败时为 null。
        detail.put("judgeReason", judge == null ? null : judge.reason());

        // 组装单条 trace 的评估结果，分数统一转成百分制展示。
        return new TraceEvaluationResult(
                // 当前 trace 的唯一 ID。
                row.getTraceId(),
                // 当前 trace 所属 session。
                row.getSessionId(),
                // 当前 trace 的创建时间。
                row.getCreatedAt(),
                // 单条总分，百分制。
                toPercent(score),
                // 规则分，百分制。
                toPercent(ruleScore),
                // LLM Judge 分，百分制。
                toPercent(llmJudgeScore),
                // 用户反馈分，百分制。
                toPercent(userFeedbackScore),
                // 指标明细，包含原始指标和归一化指标。
                metrics,
                // 诊断信息，供后台展开查看。
                detail
        );
    }

    private TraceSnapshot parseTrace(RequestTraceRow row) {
        // 最终意图，优先从 INTENT_REVISED 事件里读取。
        String intent = null;
        // 澄清动作，来自 CLARIFY_DECISION 的 action 字段。
        String clarifyAction = null;
        // token 累加器，只累加 AGENT_CALL 事件里的 totalTokens。
        Long tokenCost = 0L;
        // 标记是否真的拿到了 token，避免无 token 数据时误报 0。
        boolean hasToken = false;
        // FAILED trace 默认视为 fallback/异常链路。
        boolean fallbackUsed = "FAILED".equalsIgnoreCase(row.getStatus());
        // 重排候选 ID 集合，用于判断最终展示卡片是否来自候选。
        Set<Long> rankedIds = new LinkedHashSet<>();
        // 最终响应卡片 ID 集合，用于幻觉检查和多轮一致性检查。
        Set<Long> responseIds = new LinkedHashSet<>();
        // 调整场景中的已排除 ID，来自 ADJUST_CONTEXT_RESOLVED。
        List<Long> excludedIds = List.of();
        // 最终对用户展示的文本，用于安全合规和 Judge 输入。
        String finalText = "";
        // 最终槽位，优先取 SLOTS_MERGED，其次取 INTENT_REVISED.slots。
        Map<String, List<String>> slots = Map.of();

        // trace_json 的根节点里 events 数组保存了本轮请求的全部链路事件。
        JsonNode events = readTree(row.getTraceJson()).path("events");
        // 只有 events 是数组时才逐个事件解析，异常 JSON 会得到空对象并跳过。
        if (events.isArray()) {
            // 遍历每一个 TraceEvent，按 eventType 提取需要的评估字段。
            for (JsonNode event : events) {
                // eventType 决定当前事件属于意图、槽位、澄清、排序还是响应阶段。
                String eventType = event.path("eventType").asText();
                // 任意事件含 errorMessage 或出现 REQUEST_FAILED，都认为本轮有 fallback/失败风险。
                if (hasText(event.path("errorMessage").asText(null)) || "REQUEST_FAILED".equals(eventType)) {
                    // 标记 fallbackUsed，后续 fallbackRate=1，fallbackScore=0。
                    fallbackUsed = true;
                }
                // AGENT_CALL 事件记录了模型 token 使用量。
                if ("AGENT_CALL".equals(eventType) && event.path("totalTokens").isNumber()) {
                    // 将每次 Agent 调用的 token 累加成整条 trace 的 tokenCost。
                    tokenCost += event.path("totalTokens").asLong();
                    // 标记 token 数据存在，避免最后把无数据误当作 0 token。
                    hasToken = true;
                }
                // outputPayload 在 trace 中是字符串 JSON，这里解析成 JsonNode 方便按字段读取。
                JsonNode output = payload(event.path("outputPayload").asText(null));
                // INTENT_REVISED 是 Orchestrator 修正后的最终意图，比 raw intent 更适合评估。
                if ("INTENT_REVISED".equals(eventType)) {
                    // 读取修正后的 intent，如果字段缺失则保留已有值。
                    intent = output.path("intent").asText(intent);
                    // 有些 trace 会在 INTENT_REVISED 里带 slots，作为槽位备选来源。
                    if (output.has("slots")) {
                        // 将 slots JSON 归一成 Map<String, List<String>>。
                        slots = slots(output.path("slots"));
                    }
                // SLOTS_MERGED 是历史槽位和本轮槽位合并后的最终槽位。
                } else if ("SLOTS_MERGED".equals(eventType)) {
                    // 用合并后的槽位覆盖前面的备选槽位。
                    slots = slots(output);
                // CLARIFY_DECISION 记录澄清节点 ASK/READY 的结构化结果。
                } else if ("CLARIFY_DECISION".equals(eventType)) {
                    // 提取 clarify action，用于和 expected_clarify_action 比较。
                    clarifyAction = output.path("action").asText(clarifyAction);
                // ADJUST_CONTEXT_RESOLVED 表示“换一批/清淡点”等多轮调整链路。
                } else if ("ADJUST_CONTEXT_RESOLVED".equals(eventType)) {
                    // 读取本轮应该排除的历史推荐 ID。
                    excludedIds = longList(output.path("excludeMealIds"));
                    // 调整链路强制视为 MEAL_ADJUST，便于后续多轮一致性计算。
                    intent = "MEAL_ADJUST";
                // MEAL_RANKED 是 Java 重排后的候选集合。
                } else if ("MEAL_RANKED".equals(eventType)) {
                    // 收集 ranked[].id，后续检查最终响应是否编造候选外餐食。
                    rankedIds.addAll(ids(output.path("ranked"), "id"));
                // RESPONSE_AGENT_RESULT 是 ResponseAgent 生成自然语言和卡片后的中间结果。
                } else if ("RESPONSE_AGENT_RESULT".equals(eventType)) {
                    // 提取最终文本，后续安全合规和 Judge 会使用。
                    finalText = output.path("speechText").asText(finalText);
                    // 提取 displayBlocks[].id，后续做幻觉控制检查。
                    responseIds.addAll(ids(output.path("displayBlocks"), "id"));
                // RESPONSE_READY 是返回给前端前的最终响应事件。
                } else if ("RESPONSE_READY".equals(eventType)) {
                    // 用最终响应文本覆盖中间响应文本。
                    finalText = output.path("speechText").asText(finalText);
                    // 用最终响应卡片补充 responseIds。
                    responseIds.addAll(ids(output.path("displayBlocks"), "id"));
                }
            }
        }

        // 如果没有响应卡片，或所有响应卡片都存在于 rankedIds 中，则认为没有餐食幻觉。
        boolean hallucinationFree = responseIds.isEmpty() || rankedIds.containsAll(responseIds);
        // 安全合规先用禁用短语做轻量规则检查，Guard 结果已经体现在 trace 事件里。
        boolean safetyCompliance = FORBIDDEN_PHRASES.stream().noneMatch(finalText::contains);
        // 非调整链路不计算多轮一致性，保持 null 避免影响平均分。
        Double multiTurnConsistency = null;
        // 只有 MEAL_ADJUST 才判断是否复用了上一轮上下文并排除旧推荐。
        if ("MEAL_ADJUST".equals(intent)) {
            // 有排除列表且最终推荐没有命中排除项，则多轮一致性为 1，否则为 0。
            multiTurnConsistency = excludedIds.isEmpty() ? 0.0 : responseIds.stream().noneMatch(excludedIds::contains) ? 1.0 : 0.0;
        }
        // 把从 trace_json 中抽取出的评估事实封装成快照，供后续规则和 Judge 共同使用。
        return new TraceSnapshot(
                // 最终意图。
                intent,
                // 最终槽位。
                slots,
                // 澄清动作。
                clarifyAction,
                // token 成本；未拿到 token 时返回 null。
                hasToken ? tokenCost : null,
                // 是否出现 fallback/失败风险。
                fallbackUsed,
                // 安全合规布尔结果。
                safetyCompliance,
                // 幻觉控制布尔结果。
                hallucinationFree,
                // 多轮一致性分，非调整链路为 null。
                multiTurnConsistency,
                // 最终回复文本。
                finalText,
                // 最终推荐卡片数量。
                responseIds.size()
        );
    }

    private Double intentAccuracy(String expectedIntent, String actualIntent) {
        // 未标注 expectedIntent 时，该指标不参与当前 trace 的规则分。
        if (!hasText(expectedIntent)) {
            // 返回 null 表示缺少 gold label，而不是错误。
            return null;
        }
        // 标注意图和预测意图完全一致得 1，否则得 0。
        return expectedIntent.equals(actualIntent) ? 1.0 : 0.0;
    }

    private Double slotAccuracy(String expectedSlotsJson, Map<String, List<String>> actualSlots) {
        // 未标注 expectedSlots 时，槽位准确率不参与规则分。
        if (!hasText(expectedSlotsJson)) {
            // 返回 null，避免把未标注 trace 当作槽位错误。
            return null;
        }
        // 将人工标注的 JSON 槽位解析成标准 Map。
        Map<String, List<String>> expectedSlots = slots(readTree(expectedSlotsJson));
        // compared 统计有标注值的槽位数量。
        int compared = 0;
        // matched 统计预测完全匹配的槽位数量。
        int matched = 0;
        // 遍历 7 个标准槽位逐项比较。
        for (String slotName : SLOT_NAMES) {
            // 取出当前槽位的人工标注值。
            List<String> expected = expectedSlots.getOrDefault(slotName, List.of());
            // 人工没标这个槽位时跳过，不让空标注影响分数。
            if (expected.isEmpty()) {
                // 继续比较下一个槽位。
                continue;
            }
            // 当前槽位有人工标注，纳入分母。
            compared++;
            // 预测值与标注值集合完全一致时计为命中。
            if (sameValues(expected, actualSlots.getOrDefault(slotName, List.of()))) {
                // 命中槽位数加 1。
                matched++;
            }
        }
        // 没有任何可比较槽位时返回 null，否则返回命中比例。
        return compared == 0 ? null : matched / (double) compared;
    }

    private Double clarifyAccuracy(String expectedAction, String actualAction) {
        // 未标注 expectedClarifyAction 时，不计算澄清必要性准确率。
        if (!hasText(expectedAction)) {
            // null 表示缺少标注，而不是澄清错误。
            return null;
        }
        // ASK/READY 与人工标注完全一致得 1，否则得 0。
        return expectedAction.equals(actualAction) ? 1.0 : 0.0;
    }

    private Double costScore(Long tokenCost) {
        // 如果模型没有返回 token usage，则成本分不参与规则分。
        if (tokenCost == null) {
            // 返回 null 表示没有观测值。
            return null;
        }
        // 1000 token 以内视为成本优秀，给满分。
        if (tokenCost <= 1000) {
            // 满分 1。
            return 1.0;
        }
        // 3000 token 以上视为成本过高，给 0 分。
        if (tokenCost >= 3000) {
            // 最低分 0。
            return 0.0;
        }
        // 1000 到 3000 之间线性衰减。
        return (3000.0 - tokenCost) / 2000.0;
    }

    private Double latencyScore(Long latencyMs) {
        // durationMs 缺失时不计算延迟分。
        if (latencyMs == null) {
            // 返回 null 表示没有观测值。
            return null;
        }
        // 3 秒以内满足文档目标，给满分。
        if (latencyMs <= 3000) {
            // 满分 1。
            return 1.0;
        }
        // 8 秒以上体验较差，给 0 分。
        if (latencyMs >= 8000) {
            // 最低分 0。
            return 0.0;
        }
        // 3 秒到 8 秒之间线性衰减。
        return (8000.0 - latencyMs) / 5000.0;
    }

    private Double feedbackScore(List<FeedbackRow> feedbacks) {
        // 将当前 session 下的多条用户反馈逐条映射成 0-1 分。
        List<Double> scores = feedbacks.stream()
                // 单条反馈优先使用 rating，其次使用 action。
                .map(this::singleFeedbackScore)
                // 无法识别的反馈动作返回 null，这里过滤掉。
                .filter(Objects::nonNull)
                // 收集所有可用反馈分。
                .toList();
        // 多条反馈取平均；没有可用反馈时返回 null。
        return average(scores);
    }

    private Double singleFeedbackScore(FeedbackRow feedback) {
        // 如果用户给了 rating，优先使用 1-5 星评分。
        if (feedback.getRating() != null) {
            // rating 截断到 0-5，再归一化到 0-1。
            return Math.max(0, Math.min(5, feedback.getRating())) / 5.0;
        }
        // 没有 rating 时，把 action 转成大写做枚举匹配。
        String action = feedback.getAction() == null ? "" : feedback.getAction().toUpperCase(Locale.ROOT);
        // 根据反馈动作映射成粗粒度满意度分。
        return switch (action) {
            // 正向动作认为用户满意，给 1 分。
            case "LIKE", "UP", "ADOPT", "ACCEPT" -> 1.0;
            // 负向动作认为用户不满意，给 0 分。
            case "DISLIKE", "DOWN", "REJECT" -> 0.0;
            // 换一批类动作说明不完全满意，但不等于彻底错误，给 0.4。
            case "SWITCH", "CHANGE", "REFRESH" -> 0.4;
            // 未知动作不参与反馈分。
            default -> null;
        };
    }

    private Double weightedScore(Double ruleScore, Double judgeScore, Double feedbackScore) {
        // weighted 保存加权后的分子。
        double weighted = 0.0;
        // weight 保存实际参与计算的权重和。
        double weight = 0.0;
        // 规则分存在时按 60% 权重计入总分。
        if (ruleScore != null) {
            // 累加规则分贡献。
            weighted += ruleScore * 0.6;
            // 累加规则分权重。
            weight += 0.6;
        }
        // Judge 分存在时按 10% 权重计入总分。
        if (judgeScore != null) {
            // 累加 Judge 分贡献。
            weighted += judgeScore * 0.1;
            // 累加 Judge 分权重。
            weight += 0.1;
        }
        // 用户反馈分存在时按 30% 权重计入总分。
        if (feedbackScore != null) {
            // 累加用户反馈分贡献。
            weighted += feedbackScore * 0.3;
            // 累加用户反馈分权重。
            weight += 0.3;
        }
        // 如果三个分数组都缺失，返回 null；否则按实际权重归一。
        return weight == 0.0 ? null : weighted / weight;
    }

    private Map<String, Double> metricAverages(List<TraceEvaluationResult> results) {
        // values 用来按指标名收集所有 trace 的非空指标值。
        Map<String, List<Double>> values = new LinkedHashMap<>();
        // 遍历每条 trace 的评估结果。
        for (TraceEvaluationResult result : results) {
            // 遍历当前 trace 的所有指标。
            result.metrics().forEach((name, value) -> {
                // null 表示当前 trace 不具备该指标的计算条件，不参与平均。
                if (value != null) {
                    // 按指标名归组，后续统一求平均。
                    values.computeIfAbsent(name, ignored -> new ArrayList<>()).add(value);
                }
            });
        }
        // averages 用来保存每个指标的区间平均值。
        Map<String, Double> averages = new LinkedHashMap<>();
        // 对每个指标组求平均，保持输出顺序稳定。
        values.forEach((name, metricValues) -> averages.put(name, average(metricValues)));
        // 返回指标平均值 Map。
        return averages;
    }

    private Double groupAverage(Map<String, Double> metrics, List<String> names) {
        // 从 metrics 中按指定名字取出分数，再复用 average 跳过 null。
        return average(names.stream().map(metrics::get).toList());
    }

    private Double average(List<Double> values) {
        // 过滤 null，null 表示指标缺失而不是 0 分。
        List<Double> present = values.stream().filter(Objects::nonNull).toList();
        // 如果没有任何可用值，平均值也应该是 null。
        if (present.isEmpty()) {
            // 返回 null，避免误导为 0 分。
            return null;
        }
        // 对所有可用值求算术平均。
        return present.stream().mapToDouble(Double::doubleValue).average().orElseThrow();
    }

    private Double toPercent(Double score) {
        // 内部分数是 0-1，输出展示统一转百分制；null 保持 null。
        return score == null ? null : Math.round(score * 10000.0) / 100.0;
    }

    private JsonNode payload(String payload) {
        // outputPayload 为空时返回空对象，简化调用方 path 读取。
        if (!hasText(payload)) {
            // 空对象 path 任意字段都会得到 missing node。
            return objectMapper.createObjectNode();
        }
        // 非空 payload 按 JSON 解析，解析失败也会返回空对象。
        return readTree(payload);
    }

    private JsonNode readTree(String json) {
        // 空字符串没有可解析内容，直接返回空对象。
        if (!hasText(json)) {
            // 返回空对象而不是抛异常，保证评估不会被单条坏 trace 中断。
            return objectMapper.createObjectNode();
        }
        // 尝试用 Jackson 解析 JSON 字符串。
        try {
            // 解析成功返回 JsonNode。
            return objectMapper.readTree(json);
        // 捕获所有解析异常，避免脏 trace 影响整个评估任务。
        } catch (Exception ignored) {
            // 解析失败时返回空对象，相关指标会自然变成 null 或默认值。
            return objectMapper.createObjectNode();
        }
    }

    private Map<String, List<String>> slots(JsonNode node) {
        // 创建槽位 Map，key 固定为 7 个标准槽位。
        Map<String, List<String>> slots = new HashMap<>();
        // 遍历标准槽位名，避免输出里出现非标准字段。
        for (String slotName : SLOT_NAMES) {
            // 将当前槽位节点解析成字符串列表。
            slots.put(slotName, stringList(node.path(slotName)));
        }
        // 返回标准化后的槽位 Map。
        return slots;
    }

    private List<String> stringList(JsonNode node) {
        // 节点为空、缺失或 JSON null 时，统一视为空列表。
        if (node == null || node.isMissingNode() || node.isNull()) {
            // 空槽位不参与槽位准确率的命中判断。
            return List.of();
        }
        // 标准槽位通常是数组，这里按数组逐项提取字符串。
        if (node.isArray()) {
            // 临时收集有效字符串值。
            List<String> values = new ArrayList<>();
            // 遍历数组内每个值。
            node.forEach(value -> {
                // 只保留非空字符串，过滤 null 和空白。
                if (hasText(value.asText(null))) {
                    // trim 后加入结果列表。
                    values.add(value.asText().trim());
                }
            });
            // 去重后返回，避免重复槽位影响集合比较。
            return values.stream().distinct().toList();
        }
        // 兼容单值字符串格式，将其包装成单元素列表。
        return hasText(node.asText(null)) ? List.of(node.asText().trim()) : List.of();
    }

    private List<Long> longList(JsonNode node) {
        // excludeMealIds 等字段应为数组；非数组时返回空列表。
        if (node == null || !node.isArray()) {
            // 空列表表示当前 trace 没有可用的排除 ID。
            return List.of();
        }
        // 临时收集数组里的 long 值。
        List<Long> values = new ArrayList<>();
        // 遍历 JSON 数组。
        node.forEach(value -> {
            // 只接收数字节点，忽略字符串或其他脏数据。
            if (value.isNumber()) {
                // 将数字节点转为 long 后加入结果。
                values.add(value.asLong());
            }
        });
        // 返回解析出的 long 列表。
        return values;
    }

    private Set<Long> ids(JsonNode array, String fieldName) {
        // 使用 LinkedHashSet 去重并尽量保留 trace 中原始顺序。
        Set<Long> ids = new LinkedHashSet<>();
        // 只有输入是数组时才尝试提取 ID。
        if (array != null && array.isArray()) {
            // 遍历数组内每个对象。
            array.forEach(item -> {
                // 只在指定字段是数字时读取，避免脏字段导致异常。
                if (item.path(fieldName).isNumber()) {
                    // 将指定字段转为 long 放入集合。
                    ids.add(item.path(fieldName).asLong());
                }
            });
        }
        // 返回提取到的 ID 集合。
        return ids;
    }

    private boolean sameValues(List<String> expected, List<String> actual) {
        // 槽位比较使用集合语义，忽略顺序但不忽略缺失或多余值。
        return new LinkedHashSet<>(expected).equals(new LinkedHashSet<>(actual));
    }

    private Map<String, Object> buildJudgeInput(TraceSnapshot snapshot) {
        // Judge 输入只放评估必要摘要，避免把完整 trace_json 塞给模型。
        Map<String, Object> input = new LinkedHashMap<>();
        // 提供规则解析出的最终意图，帮助 Judge 理解回答场景。
        input.put("predictedIntent", snapshot.intent());
        // 提供规则解析出的槽位，帮助 Judge 判断解释是否贴合需求。
        input.put("predictedSlots", snapshot.slots());
        // 提供澄清动作，帮助 Judge 理解当前回复是否是追问。
        input.put("predictedClarifyAction", snapshot.clarifyAction());
        // 提供最终回复文本，Judge 主要基于它评解释质量和自然度。
        input.put("finalReply", snapshot.finalText());
        // 提供推荐卡片数量，帮助 Judge 区分推荐回答和纯文本回答。
        input.put("recommendationCount", snapshot.recommendationCount());
        // 提供规则安全结果，Judge Prompt 明确不重复评安全，但可作为背景。
        input.put("safetyComplianceByRule", snapshot.safetyCompliance());
        // 提供规则幻觉结果，Judge Prompt 明确不重复评幻觉，但可作为背景。
        input.put("hallucinationFreeByRule", snapshot.hallucinationFree());
        // 返回给 EvaluationJudgeService 序列化成 JSON prompt。
        return input;
    }

    private boolean hasAnyLabel(RequestTraceRow row) {
        // 任意一个人工标注字段存在，就认为这条 trace 已经部分标注。
        return hasText(row.getExpectedIntent()) || hasText(row.getExpectedSlots()) || hasText(row.getExpectedClarifyAction());
    }

    private boolean hasText(String text) {
        // 统一判断字符串是否非 null 且非空白，避免重复写 null/blank 判断。
        return text != null && !text.isBlank();
    }

    // TraceSnapshot 是从 trace_json 解析出的评估事实快照，避免后续逻辑反复读 JSON。
    private record TraceSnapshot(
            // 最终意图。
            String intent,
            // 最终槽位。
            Map<String, List<String>> slots,
            // 澄清动作 ASK/READY。
            String clarifyAction,
            // token 总成本。
            Long tokenCost,
            // 是否触发 fallback 或失败。
            boolean fallbackUsed,
            // 规则判断的安全合规结果。
            boolean safetyCompliance,
            // 规则判断的幻觉控制结果。
            boolean hallucinationFree,
            // 多轮调整一致性分。
            Double multiTurnConsistency,
            // 最终回复文本。
            String finalText,
            // 最终推荐卡片数量。
            int recommendationCount
    ) {
    }
}