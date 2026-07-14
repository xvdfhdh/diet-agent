package com.diet.service.intent;

import com.diet.enums.Intent;
import com.diet.model.IntentResult;
import com.diet.model.SessionState;
import com.diet.model.SlotBundle;
import org.springframework.stereotype.Service;

/**
 * 意图后处理服务。
 * LLM 意图识别可能误判，Orchestrator 在路由前用历史 SessionState 做二次矫正。
 */
@Service
public class IntentReviseService {

    /** 低于该阈值时，推荐意图降级为澄清，避免低确定性结果直接进入推荐。 */
    private static final double LOW_CONFIDENCE_THRESHOLD = 0.4;

    /**
     * 根据会话状态矫正 IntentAgent 输出。
     * 由 Orchestrator#handleTurn 在 INTENT_RECOGNIZED 之后调用。
     */
    public IntentResult revise(SessionState state, IntentResult result, String userInput) {
        // result 为 null 时构造 CLARIFY_NEEDED + 空槽位，防止 NPE
        IntentResult safeResult = result == null ? IntentResult.clarify(SlotBundle.empty()) : result;

        // 规则一：健康风险优先前置拦截，即使 LLM 置信度较低也走保守风险链路
        if (safeResult.intent() == Intent.HEALTH_RISK || containsHealthRiskKeyword(userInput)) {
            return new IntentResult(Intent.HEALTH_RISK, safeSlots(safeResult), safeResult.confidence());
        }

        // 规则二：没有历史推荐时，调整意图没有可排除对象，降级为推荐主链路并由澄清规则继续判断
        if (safeResult.intent() == Intent.MEAL_ADJUST && !hasLastRecommendations(state)) {
            return new IntentResult(Intent.MEAL_RECOMMENDATION, safeSlots(safeResult), safeResult.confidence());
        }

        // 规则三：推荐意图低置信度时先进入澄清链路；健康风险已在上方优先处理，不在这里降级
        if (safeResult.intent() == Intent.MEAL_RECOMMENDATION && safeResult.confidence() < LOW_CONFIDENCE_THRESHOLD) {
            return new IntentResult(Intent.CLARIFY_NEEDED, safeSlots(safeResult), safeResult.confidence());
        }

        // 无矫正规则命中，原样返回 LLM 结果
        return safeResult;
    }

    /** 判断会话是否已有可用于“换一批”的上轮推荐结果。 */
    private boolean hasLastRecommendations(SessionState state) {
        return state != null && state.lastRecommendations() != null && !state.lastRecommendations().isEmpty();
    }

    /** slots 为空时使用空槽位，避免后续合并逻辑出现 NPE。 */
    private SlotBundle safeSlots(IntentResult result) {
        return result.slots() == null ? SlotBundle.empty() : result.slots();
    }

    /** 健康风险关键词命中时，Java 规则直接前置拦截。 */
    private boolean containsHealthRiskKeyword(String userInput) {
        if (userInput == null || userInput.isBlank()) {
            return false;
        }
        return containsAny(userInput, "胃疼", "糖尿病", "孕妇", "未成年人", "儿童", "高血压", "治好", "治疗", "诊断", "处方", "极端节食", "绝食", "一天不吃", "只喝水");
    }

    /** 判断 text 是否包含 keywords 中任一子串。 */
    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
}