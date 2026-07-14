package com.diet.service.session;

import com.diet.enums.Intent;
import com.diet.mapper.SessionMapper;
import com.diet.model.ConversationTurn;
import com.diet.model.SessionMessageRow;
import com.diet.model.SessionRow;
import com.diet.util.JsonService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * 会话消息落库服务。
 * 负责 diet_sessions 创建和 diet_messages 追加；会话状态（slots/phase）由 SessionStateService 管理。
 */
@Service
public class SessionService {

    /** MyBatis Mapper，操作 diet_sessions 和 diet_messages 表。 */
    private final SessionMapper sessionMapper;

    /** JSON 序列化工具。 */
    private final JsonService jsonService;

    /** 注入 IntentAgent 的最近对话条数上限，来自配置 diet.session.max-history-turns。 */
    private final int maxHistoryTurns;

    /** 构造器注入依赖。 */
    public SessionService(
            SessionMapper sessionMapper,
            JsonService jsonService,
            @Value("${diet.session.max-history-turns:10}") int maxHistoryTurns
    ) {
        this.sessionMapper = sessionMapper;
        this.jsonService = jsonService;
        this.maxHistoryTurns = maxHistoryTurns;
    }

    /** 创建新会话并返回 sessionId（旧接口，Orchestrator 优先走 SessionStateService）。 */
    public String createSession(Long userId) {
        SessionRow row = new SessionRow();
        row.setId("sess_" + UUID.randomUUID().toString().replace("-", "")); // 生成 sessionId
        row.setUserId(userId);                                               // 绑定用户
        row.setPhase("START");                                               // 初始阶段
        row.setSlots("{}");                                                  // 空 slots JSON
        row.setLastRecommendations(jsonService.toJsonArray(List.of()));      // 空推荐 ID 列表
        sessionMapper.insert(row);                                           // INSERT diet_sessions
        return row.getId();                                                  // 返回 sessionId
    }

    /** 确保 sessionId 对应行存在，不存在则插入空会话。 */
    public void ensureSession(String sessionId, Long userId) {
        if (sessionMapper.findById(sessionId, userId) == null) {
            SessionRow row = new SessionRow();
            row.setId(sessionId);
            row.setUserId(userId);
            row.setPhase("START");
            row.setSlots("{}");
            row.setLastRecommendations(jsonService.toJsonArray(List.of()));
            sessionMapper.insert(row);
        }
    }

    /**
     * 追加一条对话消息到 diet_messages 表。
     * 由 Orchestrator 在每轮用户/助手消息产生时调用。
     */
    public void appendMessage(String sessionId, String role, String content, String intent, String traceId) {
        // INSERT：sessionId + role(user/assistant) + content + intent + traceId
        sessionMapper.insertMessage(sessionId, role, content == null ? "" : content, intent, traceId);
    }

    /**
     * 读取最近 n 条对话消息并转为 IntentAgent 使用的短期上下文。
     */
    public List<ConversationTurn> recentConversationTurns(String sessionId, Long userId, int n) {
        if (sessionId == null || sessionId.isBlank() || userId == null || n <= 0) {
            return List.of();
        }
        int limit = Math.min(n, Math.max(1, maxHistoryTurns));
        List<SessionMessageRow> rows = sessionMapper.listRecentMessages(sessionId, userId, limit);
        Collections.reverse(rows); // SQL 倒序取最近消息，prompt 中按时间正序注入。
        return rows.stream()
                .map(this::toConversationTurn)
                .toList();
    }

    /** 将数据库消息行映射为 IntentAgent 使用的短期上下文摘要。 */
    private ConversationTurn toConversationTurn(SessionMessageRow row) {
        return new ConversationTurn(
                row.getRole(),
                parseIntent(row.getIntent()),
                summarize(row.getContent()),
                row.getCreatedAt() == null
                        ? System.currentTimeMillis()
                        : row.getCreatedAt().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        );
    }

    /** 解析消息 intent，脏数据返回 null，避免影响主链路。 */
    private Intent parseIntent(String intent) {
        try {
            return intent == null || intent.isBlank() ? null : Intent.valueOf(intent);
        } catch (Exception ignored) {
            return null;
        }
    }

    /** 将长文本压缩为最多 120 字符的摘要，减少 IntentAgent 输入 token。 */
    private String summarize(String text) {
        if (text == null) {
            return "";
        }
        String normalized = text.replace("\r", "").replace("\n", " ").trim();
        return normalized.length() <= 120 ? normalized : normalized.substring(0, 120);
    }
}