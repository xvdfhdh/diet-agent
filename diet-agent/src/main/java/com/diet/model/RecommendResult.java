package com.diet.model;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * RecommendAgent 的结构化输出。
 * 它负责推荐理由和策略说明，不负责检索、不负责排序。
 */
@Data
@Accessors(fluent = true)
@AllArgsConstructor
public class RecommendResult {
    /** 推荐项列表，通常取 2 到 3 个展示给用户。 */
    private List<RecommendedMealOption> recommendations;
    /** 是否需要最终回答追加健康边界提示。 */
    private boolean needDisclaimer;

    /** 空推荐结果，用于无候选或检索失败兜底。 */
    public static RecommendResult empty() {
        return new RecommendResult(List.of(), false);
    }
}