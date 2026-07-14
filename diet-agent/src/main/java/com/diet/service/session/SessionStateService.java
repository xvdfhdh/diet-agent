package com.diet.service.session;

import com.diet.exception.DietException;
import com.diet.mapper.SessionMapper;
import com.diet.enums.Intent;
import com.diet.enums.SessionPhase;
import com.diet.model.SessionRow;
import com.diet.model.SessionState;
import com.diet.model.SlotBundle;
import com.diet.enums.SourceMode;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.UUID;

/**
 * 会话状态读写服务。
 * Orchestrator 是唯一写 SessionState 的角色；dietChat 链路中 loadOrCreate 和 save 被频繁调用。
 */
@Service
public class SessionStateService {

    /**
     * last_recommendations 列反序列化为 List&lt;Long&gt; 的类型引用。
     */
    private static final TypeReference<List<Long>> LONG_LIST = new TypeReference<>() {
    };

    /**
     * MyBatis Mapper，读写 diet_sessions 表。
     */
    private final SessionMapper sessionMapper;

    /**
     * Jackson，序列化/反序列化 slots JSON 和 last_recommendations JSON。
     */
    private final ObjectMapper objectMapper;

    /**
     * 构造器注入 Mapper 和 ObjectMapper。
     */
    public SessionStateService(SessionMapper sessionMapper, ObjectMapper objectMapper) {
        this.sessionMapper = sessionMapper;
        this.objectMapper = objectMapper;
    }

    /**
     * 创建新会话：生成 sessionId + 初始 SessionState + INSERT。
     */
    public SessionState create(Long userId, SourceMode sourceMode) {
        String sessionId = "sess_" + UUID.randomUUID().toString().replace("-", ""); // 生成唯一 sessionId
        SessionState state = SessionState.fresh(sessionId, userId, sourceMode);     // 构造初始状态（空 slots、START phase）
        insert(state);                                                               // INSERT diet_sessions
        return state;
    }

    /**
     * 加载或创建会话状态。
     * 由 Orchestrator#dietChat 入口调用，返回 slots/phase/lastRecommendations 等。
     */
    public SessionState loadOrCreate(String sessionId, Long userId, SourceMode sourceMode) {
        // 请求未带 sessionId 时自动创建新会话
        if (sessionId == null || sessionId.isBlank()) {
            return create(userId, sourceMode);
        }
        // 按 sessionId + userId 查 diet_sessions（校验归属）
        SessionRow row = sessionMapper.findById(sessionId, userId);
        // 不存在则用请求的 sessionId 创建新行
        if (row == null) {
            SessionState state = SessionState.fresh(sessionId, userId, sourceMode);
            insert(state);
            return state;
        }
        // 存在则从 DB 行反序列化为 SessionState
        return fromRow(row, sourceMode);
    }

    /**
     * 持久化 Orchestrator 更新后的会话状态。
     * 澄清/推荐/固定回复分支结束前都会调用。
     */
    public void save(SessionState state) {
        SessionRow row = toRow(state);              // SessionState → SessionRow
        int updated = sessionMapper.update(row);    // UPDATE diet_sessions
        if (updated == 0) {
            throw new DietException("会话状态保存失败"); // 影响行数 0 说明 sessionId 不存在或并发冲突
        }
    }

    /**
     * INSERT 新会话行到 diet_sessions。
     */
    private void insert(SessionState state) {
        sessionMapper.insert(toRow(state));
    }

    /**
     * 将 diet_sessions 行反序列化为 SessionState 业务对象。
     */
    private SessionState fromRow(SessionRow row, SourceMode requestSourceMode) {
        try {
            JsonNode root = parseObject(row.getSlots());                                    // 解析 slots JSON
            JsonNode meta = root.path("_meta");                                             // _meta 存 sourceMode/intent 等
            SourceMode sourceMode = parseSourceMode(meta.path("sourceMode").asText(null), requestSourceMode);
            Intent currentIntent = parseIntent(meta.path("currentIntent").asText(null));
            // 7 维槽位从 slots JSON 各字段读取
            SlotBundle slots = new SlotBundle(
                    readStringList(root, "mealTime"),
                    readStringList(root, "mood"),
                    readStringList(root, "scene"),
                    readStringList(root, "healthGoal"),
                    readStringList(root, "cuisine"),
                    readStringList(root, "taste"),
                    readStringList(root, "convenience")
            );
            return new SessionState(
                    row.getId(),                           // sessionId
                    row.getUserId(),                         // userId
                    parsePhase(row.getPhase()),              // phase 枚举
                    sourceMode,                              // PERSONAL / PUBLIC
                    currentIntent,                           // 当前意图
                    slots,                                   // 槽位
                    parseLongList(row.getLastRecommendations()) // 上轮推荐 ID 列表
            );
        } catch (Exception e) {
            throw new DietException("会话状态解析失败", e);
        }
    }

    /**
     * SessionState 转为 SessionRow，供 INSERT/UPDATE。
     */
    private SessionRow toRow(SessionState state) {
        SessionRow row = new SessionRow();
        row.setId(state.sessionId());
        row.setUserId(state.userId());
        row.setPhase(state.phase().name());
        row.setSlots(toSlotsJson(state));                          // slots + _meta 序列化
        row.setLastRecommendations(toJson(state.lastRecommendations())); // 推荐 ID 列表 JSON
        return row;
    }

    /**
     * 将 SlotBundle 7 维 + _meta（sourceMode/currentIntent）序列化为 slots 列 JSON。
     */
    private String toSlotsJson(SessionState state) {
        ObjectNode root = objectMapper.createObjectNode();
        root.set("mealTime", objectMapper.valueToTree(state.slots().mealTime()));
        root.set("mood", objectMapper.valueToTree(state.slots().mood()));
        root.set("scene", objectMapper.valueToTree(state.slots().scene()));
        root.set("healthGoal", objectMapper.valueToTree(state.slots().healthGoal()));
        root.set("cuisine", objectMapper.valueToTree(state.slots().cuisine()));
        root.set("taste", objectMapper.valueToTree(state.slots().taste()));
        root.set("convenience", objectMapper.valueToTree(state.slots().convenience()));
        ObjectNode meta = objectMapper.createObjectNode();
        meta.put("sourceMode", state.sourceMode() == null ? null : state.sourceMode().name());
        meta.put("currentIntent", state.currentIntent() == null ? null : state.currentIntent().name());
        root.set("_meta", meta);
        return root.toString();
    }

    /**
     * 解析 JSON 字符串；空/null 返回空 ObjectNode。
     */
    private JsonNode parseObject(String json) throws Exception {
        if (json == null || json.isBlank()) {
            return objectMapper.createObjectNode();
        }
        return objectMapper.readTree(json);
    }

    /**
     * 从 JSON 对象读取指定字段的字符串数组。
     */
    private List<String> readStringList(JsonNode root, String field) throws Exception {
        JsonNode node = root.path(field);
        if (!node.isArray()) {
            return List.of();
        }
        return objectMapper.readValue(node.toString(), new TypeReference<List<String>>() {
        });
    }

    /**
     * 解析 last_recommendations 列 JSON 为 List&lt;Long&gt;。
     */
    private List<Long> parseLongList(String json) throws Exception {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        return objectMapper.readValue(json, LONG_LIST);
    }

    /**
     * 对象序列化为 JSON 字符串。
     */
    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? List.of() : value);
        } catch (Exception e) {
            throw new DietException("会话状态 JSON 序列化失败", e);
        }
    }

    /**
     * 解析 phase 字符串为 SessionPhase 枚举，脏数据回退 START。
     */
    private SessionPhase parsePhase(String phase) {
        try {
            return phase == null ? SessionPhase.START : SessionPhase.valueOf(phase);
        } catch (Exception ignored) {
            return SessionPhase.START;
        }
    }

    /**
     * 解析 intent 字符串为 Intent 枚举，缺失/脏数据返回 null。
     */
    private Intent parseIntent(String intent) {
        try {
            return intent == null || intent.isBlank() ? null : Intent.valueOf(intent);
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * 解析 sourceMode：DB _meta 优先，请求值兜底。
     */
    private SourceMode parseSourceMode(String savedSourceMode, SourceMode requestSourceMode) {
        try {
            return savedSourceMode == null || savedSourceMode.isBlank() ? requestSourceMode : SourceMode.valueOf(savedSourceMode);
        } catch (Exception ignored) {
            return requestSourceMode;
        }
    }
}