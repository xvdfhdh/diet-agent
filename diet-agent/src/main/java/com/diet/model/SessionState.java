package com.diet.model;

import java.util.LinkedHashSet;
import java.util.List;

import com.diet.enums.Intent;
import com.diet.enums.SessionPhase;
import com.diet.enums.SourceMode;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * Orchestrator 独占写入的会话状态。
 * 该对象对应技术方案中的 SessionState，用于保存多轮对话中的槽位、阶段和上一轮推荐。
 */
@Data
@Accessors(fluent = true)
@AllArgsConstructor
public class SessionState {
    /** 当前会话 ID，前端多轮请求必须复用该值。 */
    private String sessionId;
    /** 当前用户 ID，用于 PERSONAL 数据源隔离。 */
    private Long userId;
    /** 当前会话阶段，决定下一轮上下文如何被解释。 */
    private SessionPhase phase;
    /** 当前会话绑定的数据源模式，禁止 PERSONAL 和 PUBLIC 自动混查。 */
    private SourceMode sourceMode;
    /** 当前或上一轮被 Orchestrator 确认的意图。 */
    private Intent currentIntent;
    /** 多轮累积后的标准 7 槽位。 */
    private SlotBundle slots;
    /** 本会话已推荐过的餐食 ID（累积），用于“换一批”时排除重复。 */
    private List<Long> lastRecommendations;

    /**
     * 创建一个新的空状态。
     * 该工厂方法用于数据库首次创建会话或旧数据缺少元信息时兜底。
     */
    public static SessionState fresh(String sessionId, Long userId, SourceMode sourceMode) {
        return new SessionState(
                sessionId,
                userId,
                SessionPhase.START,
                sourceMode,
                null,
                SlotBundle.empty(),
                List.of()
        );
    }

    /** 返回更新阶段后的新状态。 */
    public SessionState withPhase(SessionPhase newPhase) {
        return new SessionState(sessionId, userId, newPhase, sourceMode, currentIntent, slots, lastRecommendations);
    }

    /** 返回更新意图后的新状态。 */
    public SessionState withIntent(Intent newIntent) {
        return new SessionState(sessionId, userId, phase, sourceMode, newIntent, slots, lastRecommendations);
    }

    /** 返回更新槽位后的新状态。 */
    public SessionState withSlots(SlotBundle newSlots) {
        return new SessionState(sessionId, userId, phase, sourceMode, currentIntent, newSlots, lastRecommendations);
    }

    /** 返回更新推荐历史后的新状态（覆盖）。 */
    public SessionState withLastRecommendations(List<Long> newLastRecommendations) {
        return new SessionState(sessionId, userId, phase, sourceMode, currentIntent, slots, newLastRecommendations == null ? List.of() : List.copyOf(newLastRecommendations));
    }

    /** 将本轮推荐 ID 追加到累积历史，去重并保持插入顺序。 */
    public SessionState appendLastRecommendations(List<Long> newIds) {
        if (newIds == null || newIds.isEmpty()) {
            return this;
        }
        LinkedHashSet<Long> merged = new LinkedHashSet<>(lastRecommendations == null ? List.of() : lastRecommendations);
        merged.addAll(newIds);
        return new SessionState(sessionId, userId, phase, sourceMode, currentIntent, slots, List.copyOf(merged));
    }

    /** 返回更新数据源模式后的新状态，主要用于首次绑定 sourceMode。 */
    public SessionState withSourceMode(SourceMode newSourceMode) {
        return new SessionState(sessionId, userId, phase, newSourceMode, currentIntent, slots, lastRecommendations);
    }
}