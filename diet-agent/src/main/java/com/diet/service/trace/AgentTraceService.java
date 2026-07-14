package com.diet.service.trace;

import com.diet.mapper.AgentTraceMapper;
import com.diet.exception.DietException;
import com.diet.model.TraceLabelRequest;
import com.diet.model.RequestTraceRow;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Agent 链路追踪服务。
 * 通过 ThreadLocal {@link TraceScope} 收集一轮请求内的状态机事件和 Agent 调用，close 时写入 agent_traces 表。
 */
@Service
public class AgentTraceService {

    /** SLF4J 日志，Trace 落库失败时打 warn。 */
    private static final Logger log = LoggerFactory.getLogger(AgentTraceService.class);

    /** 按 sessionId 查询 Trace 时的默认条数上限。 */
    private static final int DEFAULT_LIMIT = 200;

    /** 按 sessionId 查询 Trace 时的最大条数上限，防止一次拉取过多。 */
    private static final int MAX_LIMIT = 1000;

    /** 单条 input/output payload 最大字符数，超出截断并追加 ...[truncated]。 */
    private static final int MAX_PAYLOAD_LENGTH = 20000;

    /** 当前请求线程的 Trace 上下文，openTrace 设置、TraceScope#close 清除。 */
    private final ThreadLocal<TraceScope> currentScope = new ThreadLocal<>();

    /** MyBatis Mapper，负责 agent_traces 表的 INSERT/SELECT。 */
    private final AgentTraceMapper agentTraceMapper;

    /** Jackson 序列化工具，将 payload 和 trace_json 转为 JSON 字符串。 */
    private final ObjectMapper objectMapper;

    /** 构造器注入 Mapper 和 ObjectMapper。 */
    public AgentTraceService(AgentTraceMapper agentTraceMapper, ObjectMapper objectMapper) {
        this.agentTraceMapper = agentTraceMapper;
        this.objectMapper = objectMapper;
    }

    /**
     * 开启一轮 Trace 上下文。
     * 创建 TraceScope 并绑定到当前线程 ThreadLocal，供后续 recordEvent/callAgent 写入事件。
     */
    public TraceScope openTrace(String traceId, String sessionId, Long userId) {
        // 创建 TraceScope 实例，持有 traceId/sessionId/userId 和事件列表
        TraceScope scope = new TraceScope(traceId, sessionId, userId);
        // 将 scope 绑定到当前线程，record 方法通过 currentScope.get() 读取
        currentScope.set(scope);
        // 返回 scope 供 try-with-resources 在 finally 中 close
        return scope;
    }

    /**
     * 记录状态机事件（无耗时）。
     * 委托 record 方法，agentName/modelName/latency/error 均为 null。
     */
    public void recordEvent(String eventType, String phase, Object inputPayload, Object outputPayload) {
        // eventType=事件名如 REQUEST_RECEIVED；phase=阶段如 HTTP/INTENT；input/output=入参和出参对象
        record(eventType, phase, null, null, inputPayload, outputPayload, null, null, null, null, null);
    }

    /**
     * 记录状态机事件并附带耗时（毫秒）。
     * 用于 REQUEST_FINISHED 等需要记录整段处理时间的场景。
     */
    public void recordEvent(String eventType, String phase, Object inputPayload, Object outputPayload, Long latencyMs) {
        // latencyMs 为从 startedAt 到当前的毫秒差
        record(eventType, phase, null, null, inputPayload, outputPayload, latencyMs, null, null, null, null);
    }

    /**
     * 记录异常事件。
     * 将 Trace 状态标记为 FAILED，并记录 errorMessage。
     */
    public void recordError(String eventType, String phase, Object inputPayload, Exception error) {
        // outputPayload 为 null，error 非 null 时会触发 scope.markFailed
        record(eventType, phase, null, null, inputPayload, null, null, null, null, null, error);
    }

    /**
     * 统一 Agent 调用入口。
     * 执行 ReActAgent.call，成功/失败均记录 AGENT_CALL 事件（含 modelName、latency）。
     */
    public Msg callAgent(String sessionId, String agentName, String modelName, ReActAgent agent, String inputText) {
        // 记录 Agent 调用开始时间（纳秒）
        long startedAt = System.nanoTime();
        try {
            // 构造 USER 角色消息并同步调用 Agent（block 等待 LLM 返回）
            Msg response = agent.call(Msg.builder()
                    .role(MsgRole.USER)
                    .textContent(inputText)
                    .build()).block();
            // 成功：记录 AGENT_CALL 事件，input=inputText，output=response 文本，latency=耗时 ms
            recordAgentCall(sessionId, agentName, modelName, inputText, response, elapsedMs(startedAt), null);
            // 将 Agent 原始响应返回给调用方（IntentAgent/ClarifyAgent/RecommendResponseAgent）
            return response;
        } catch (RuntimeException error) {
            // 失败：记录 AGENT_CALL 事件，output=null，error 非空，并 markFailed
            recordAgentCall(sessionId, agentName, modelName, inputText, null, elapsedMs(startedAt), error);
            // 继续向上抛出，由调用方 catch 或 Orchestrator 捕获
            throw error;
        }
    }

    /** 按 traceId 查询单条链路追踪记录（供调试 API 使用）。 */
    public RequestTraceRow findByTraceId(Long userId, String traceId) {
        return agentTraceMapper.findByTraceId(userId, traceId);
    }

    /** 按 sessionId 查询最近 N 条链路追踪，limit 会被 clamp 到 [1, MAX_LIMIT]。 */
    public List<RequestTraceRow> findBySessionId(Long userId, String sessionId, Integer limit) {
        // null 时用 DEFAULT_LIMIT；否则限制在 1~MAX_LIMIT 之间
        int safeLimit = limit == null ? DEFAULT_LIMIT : Math.max(1, Math.min(MAX_LIMIT, limit));
        return agentTraceMapper.findBySessionId(userId, sessionId, safeLimit);
    }

    /** 按时间范围查询 Trace，供后台标注页和评估接口复用。 */
    public List<RequestTraceRow> findByTimeRange(Long userId, LocalDateTime startAt, LocalDateTime endAt, Boolean onlyUnlabeled, Integer limit) {
        if (startAt == null || endAt == null || !startAt.isBefore(endAt)) {
            throw new DietException("Trace 查询时间范围不合法");
        }
        int safeLimit = limit == null ? DEFAULT_LIMIT : Math.max(1, Math.min(MAX_LIMIT, limit));
        return agentTraceMapper.findByTimeRange(userId, startAt, endAt, Boolean.TRUE.equals(onlyUnlabeled), safeLimit);
    }

    /** 保存人工标注的标准答案，直接写回 diet_request_trace。 */
    public void updateLabel(Long userId, String traceId, TraceLabelRequest request) {
        if (traceId == null || traceId.isBlank()) {
            throw new DietException("traceId 不能为空");
        }
        if (request == null) {
            throw new DietException("标注内容不能为空");
        }
        String expectedIntent = request.expectedIntent() == null ? null : request.expectedIntent().name();
        String expectedSlots = request.expectedSlots() == null ? null : toTraceJson(request.expectedSlots());
        String expectedClarifyAction = request.expectedClarifyAction() == null ? null : request.expectedClarifyAction().name();
        int updated = agentTraceMapper.updateLabel(
                userId,
                traceId,
                expectedIntent,
                expectedSlots,
                expectedClarifyAction,
                userId,
                request.labelNote()
        );
        if (updated == 0) {
            throw new DietException("Trace 不存在或无权限标注");
        }
    }

    /** 将 Agent 调用结果封装为 AGENT_CALL 类型事件写入 TraceScope。 */
    private void recordAgentCall(String sessionId, String agentName, String modelName, String inputText, Msg response, long latencyMs, Exception error) {
        // 从 Msg 中提取文本内容作为 output；response 为 null 时 output 也为 null
        Object output = response == null ? null : response.getTextContent();
        Long inputTokens = inputTokens(response);
        Long outputTokens = outputTokens(response);
        Long totalTokens = totalTokens(inputTokens, outputTokens);
        // 写入 eventType=AGENT_CALL，phase=AGENT，附带 agentName/modelName/token usage
        record("AGENT_CALL", "AGENT", agentName, modelName, inputText, output, latencyMs, inputTokens, outputTokens, totalTokens, error);
    }

    /**
     * 核心记录方法：将一条 TraceEvent 追加到当前 TraceScope。
     * 若 currentScope 为 null（未 openTrace）则静默跳过。
     */
    private void record(
            String eventType,
            String phase,
            String agentName,
            String modelName,
            Object inputPayload,
            Object outputPayload,
            Long latencyMs,
            Long inputTokens,
            Long outputTokens,
            Long totalTokens,
            Exception error) {
        // 从 ThreadLocal 获取当前请求的 TraceScope
        TraceScope scope = currentScope.get();
        // 未开启 Trace 时不记录（如单元测试或未包裹 openTrace 的调用）
        if (scope == null) {
            return;
        }
        // 将异常格式化为 "ClassName: message" 字符串，null 表示无异常
        String errorMessage = error == null ? null : trim(error.getClass().getSimpleName() + ": " + error.getMessage());
        // 构造 TraceEvent：stepOrder 自增，payload 序列化为 JSON 字符串
        scope.addEvent(new TraceEvent(
                scope.nextStep(),           // 事件序号，从 1 递增
                eventType,                  // 事件类型，如 REQUEST_RECEIVED / AGENT_CALL
                phase,                      // 阶段，如 HTTP / INTENT / AGENT
                agentName,                  // Agent 名，仅 AGENT_CALL 时有值
                modelName,                  // 模型名，仅 AGENT_CALL 时有值
                toPayload(inputPayload),    // 输入 payload JSON 字符串
                toPayload(outputPayload),   // 输出 payload JSON 字符串
                latencyMs,                  // 耗时毫秒，可为 null
                inputTokens,                // 输入 token 数，仅 AGENT_CALL 时有值
                outputTokens,               // 输出 token 数，仅 AGENT_CALL 时有值
                totalTokens,                // 输入 + 输出 token 数，仅 AGENT_CALL 时有值
                errorMessage,               // 错误信息，可为 null
                LocalDateTime.now().toString() // 事件创建时间 ISO 字符串
        ));
        // 若有异常，将整轮 Trace 状态标记为 FAILED
        if (errorMessage != null) {
            scope.markFailed(errorMessage);
        }
    }

    /** 将 TraceScope 内累积的所有事件序列化为 JSON 并 INSERT 到 agent_traces 表。 */
    private void flushTrace(TraceScope scope) {
        // 构造数据库行对象 RequestTraceRow
        RequestTraceRow row = new RequestTraceRow();
        row.setTraceId(scope.traceId());           // 主键 traceId
        row.setSessionId(scope.sessionId());       // 关联 sessionId
        row.setUserId(scope.userId());             // 关联 userId
        row.setStatus(scope.status());             // SUCCESS 或 FAILED
        row.setEventCount(scope.eventCount());     // 事件总数
        row.setDurationMs(elapsedMs(scope.startedAt())); // 整轮耗时 ms
        row.setErrorMessage(scope.errorMessage()); // 失败时的错误摘要
        // 构造 trace_json 内容：traceId + sessionId + userId + status + durationMs + events 数组
        Map<String, Object> trace = new LinkedHashMap<>();
        trace.put("traceId", scope.traceId());
        trace.put("sessionId", scope.sessionId());
        trace.put("userId", scope.userId());
        trace.put("status", scope.status());
        trace.put("durationMs", row.getDurationMs());
        trace.put("events", scope.events());       // 全部 TraceEvent 列表
        // 将 trace Map 序列化为 JSON 字符串写入 trace_json 列
        row.setTraceJson(toTraceJson(trace));
        // 执行 INSERT
        agentTraceMapper.insert(row);
    }

    /** 将对象序列化为 JSON 字符串；失败时返回空 events 占位 JSON。 */
    private String toTraceJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception ignored) {
            return "{\"events\":[]}";
        }
    }

    /** 将 payload 对象转为可存储的字符串；String 直接 trim，其他对象 JSON 序列化。 */
    private String toPayload(Object payload) {
        if (payload == null) {
            return null;
        }
        if (payload instanceof String text) {
            return trim(text);
        }
        try {
            return trim(objectMapper.writeValueAsString(payload));
        } catch (Exception ignored) {
            return trim(String.valueOf(payload));
        }
    }

    /** 截断超长字符串，防止 trace_json 单条 payload 过大。 */
    private String trim(String text) {
        if (text == null || text.length() <= MAX_PAYLOAD_LENGTH) {
            return text;
        }
        return text.substring(0, MAX_PAYLOAD_LENGTH) + "...[truncated]";
    }

    /** 从 Agent 响应中提取输入 token，usage 为空时返回 null。 */
    private Long inputTokens(Msg response) {
        if (response == null || response.getChatUsage() == null) {
            return null;
        }
        Number tokens = response.getChatUsage().getInputTokens();
        return tokens == null ? null : tokens.longValue();
    }

    /** 从 Agent 响应中提取输出 token，usage 为空时返回 null。 */
    private Long outputTokens(Msg response) {
        if (response == null || response.getChatUsage() == null) {
            return null;
        }
        Number tokens = response.getChatUsage().getOutputTokens();
        return tokens == null ? null : tokens.longValue();
    }

    /** 输入和输出 token 都取到时才计算总量，避免用不完整数据误导成本统计。 */
    private Long totalTokens(Long inputTokens, Long outputTokens) {
        if (inputTokens == null || outputTokens == null) {
            return null;
        }
        return inputTokens + outputTokens;
    }

    /** 纳秒时间戳转毫秒耗时。 */
    private long elapsedMs(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }

    /** 单条 Trace 事件记录，最终序列化进 trace_json.events 数组。 */
    private record TraceEvent(
            int stepOrder,       // 事件顺序号，从 1 开始
            String eventType,    // 事件类型名
            String phase,        // 所属阶段
            String agentName,    // Agent 名称（可空）
            String modelName,    // 模型名称（可空）
            String inputPayload, // 输入 JSON 字符串（可空）
            String outputPayload,// 输出 JSON 字符串（可空）
            Long latencyMs,      // 耗时毫秒（可空）
            Long inputTokens,    // 输入 token 数（可空）
            Long outputTokens,   // 输出 token 数（可空）
            Long totalTokens,    // 总 token 数（可空）
            String errorMessage, // 错误信息（可空）
            String createdAt     // 事件时间戳
    ) {
    }

    /**
     * 一轮请求的 Trace 生命周期容器。
     * 实现 AutoCloseable，配合 try-with-resources 在 close 时 flush 到 DB。
     */
    public final class TraceScope implements AutoCloseable {

        /** 本轮 traceId。 */
        private final String traceId;

        /** 本轮 sessionId。 */
        private final String sessionId;

        /** 本轮 userId。 */
        private final Long userId;

        /** 事件序号计数器，线程安全自增。 */
        private final AtomicInteger stepOrder = new AtomicInteger(0);

        /** Trace 开启时的纳秒时间戳，用于计算 durationMs。 */
        private final long startedAt = System.nanoTime();

        /** 本 scope 内累积的全部 TraceEvent。 */
        private final List<TraceEvent> events = new ArrayList<>();

        /** Trace 整体状态，默认 SUCCESS，markFailed 后变 FAILED。 */
        private String status = "SUCCESS";

        /** 失败时的错误摘要。 */
        private String errorMessage;

        /** 是否已 close，防止重复 flush。 */
        private boolean closed;

        /** 私有构造，仅 AgentTraceService#openTrace 创建。 */
        private TraceScope(String traceId, String sessionId, Long userId) {
            this.traceId = traceId;
            this.sessionId = sessionId;
            this.userId = userId;
        }
        private String traceId() { return traceId; }
        private String sessionId() { return sessionId; }
        private Long userId() { return userId; }

        /** 返回下一个事件序号（先自增再返回）。 */
        private int nextStep() { return stepOrder.incrementAndGet(); }
        private long startedAt() { return startedAt; }

        /** 返回事件列表的不可变副本。 */
        private List<TraceEvent> events() { return List.copyOf(events); }
        private int eventCount() { return events.size(); }
        private String status() { return status; }
        private String errorMessage() { return errorMessage; }

        /** 追加一条事件到 events 列表。 */
        private void addEvent(TraceEvent event) { events.add(event); }

        /** 将 Trace 标记为 FAILED 并记录错误信息。 */
        private void markFailed(String errorMessage) {
            this.status = "FAILED";
            this.errorMessage = errorMessage;
        }

        /** close 时 flush 到 DB 并清除 ThreadLocal。 */
        @Override
        public void close() {
            // 已 close 则直接返回，避免重复 INSERT
            if (closed) {
                return;
            }
            closed = true;
            try {
                // 将 events 序列化并 INSERT agent_traces
                flushTrace(this);
            } catch (RuntimeException error) {
                // 落库失败只打 warn，不影响主业务返回
                log.warn("Failed to persist request trace: traceId={}", traceId, error);
            } finally {
                // 清除当前线程 Trace 上下文，防止线程池复用时污染
                currentScope.remove();
            }
        }
    }
}