package com.diet.service.risk;

import com.diet.model.RiskGuardResult;
import com.diet.enums.Intent;
import com.diet.model.RecommendResult;
import com.diet.model.ResponseResult;
import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.List;

/**
 * 营养与健康风险守卫。
 * 在 Orchestrator#completeRecommendation 中，LLM 生成回复后做最后一道合规检查。
 */
@Component
public class RiskGuardService {

    /**
     * 检查用户输入 + 最终回复是否含健康风险表述。
     * 命中任一规则返回 block，Orchestrator 用 conservativeMessage 替换 speechText。
     */
    public RiskGuardResult check(String userInput, Intent intent, RecommendResult recommendResult, ResponseResult responseResult) {
        List<String> reasons = new ArrayList<>();
        // 拼接用户原文和助手回复，统一扫描关键词
        String allText = (userInput == null ? "" : userInput) + " " + (responseResult == null ? "" : responseResult.speechText());
        // 规则 1：意图层已识别为 HEALTH_RISK
        if (intent == Intent.HEALTH_RISK) {
            reasons.add("命中 HEALTH_RISK 意图");
        }
        // 规则 2：医疗诊断/治疗/处方承诺
        if (containsAny(allText, "治好", "治疗", "诊断", "药", "处方")) {
            reasons.add("涉及医疗诊断或治疗承诺");
        }
        // 规则 3：极端节食建议
        if (containsAny(allText, "绝食", "一天不吃", "只喝水", "极端节食")) {
            reasons.add("涉及极端节食建议");
        }
        // 规则 4：绝对化健康/减肥承诺
        if (containsAny(allText, "保证", "一定能瘦", "最健康", "包瘦")) {
            reasons.add("涉及绝对化健康承诺");
        }
        // 规则 5：特殊人群或慢病
        if (containsAny(allText, "孕妇", "糖尿病", "高血压", "未成年人", "儿童")) {
            reasons.add("涉及特殊人群或慢病风险");
        }
        // 无命中规则 → 通过
        if (reasons.isEmpty()) {
            return RiskGuardResult.pass();
        }
        // 有命中 → 拦截，返回 reasons 和 conservativeMessage 作为 rewriteSuggestion
        return RiskGuardResult.block(reasons, conservativeMessage());
    }

    /** 高风险场景的保守固定提示文案。 */
    public String conservativeMessage() {
        return "这个问题涉及健康或医疗风险，我不能替代医生做诊断或治疗建议。可以从日常饮食角度选择清淡、均衡、不过量的餐食；如果症状明显或有慢病、孕期等情况，建议咨询医生或营养师。";
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

