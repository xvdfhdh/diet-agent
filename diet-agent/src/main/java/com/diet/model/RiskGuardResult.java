package com.diet.model;

import java.util.List;

import com.diet.enums.RiskLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * NutritionGuard 和 ComplianceGuard 的结构化判断结果。
 * Orchestrator 根据 riskLevel 决定是否放行最终回答。
 */
@Data
@Accessors(fluent = true)
@AllArgsConstructor
public class RiskGuardResult {
    /** true 表示允许继续输出，false 表示需要拦截。 */
    private boolean passed;
    /** 风险等级，高风险必须保守处理。 */
    private RiskLevel riskLevel;
    /** 被命中的风险原因，便于 Trace 和调试。 */
    private List<String> blockedReasons;
    /** 可选改写建议，普通合规问题可以通过改写修复。 */
    private String rewriteSuggestion;

    /** 低风险放行结果。 */
    public static RiskGuardResult pass() {
        return new RiskGuardResult(true, RiskLevel.LOW, List.of(), null);
    }

    /** 高风险拦截结果。 */
    public static RiskGuardResult block(List<String> reasons, String suggestion) {
        return new RiskGuardResult(false, RiskLevel.HIGH, reasons == null ? List.of() : List.copyOf(reasons), suggestion);
    }
}




