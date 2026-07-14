package com.diet.model;

import com.diet.enums.SourceMode;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * RecommendAgent 生成理由后的单个推荐项。
 * matchScore 由 Java 重排层生成，Agent 只能引用，不能自行改分。
 */
@Data
@Accessors(fluent = true)
@AllArgsConstructor
public class RecommendedMealOption {
    /** 餐食 ID，必须来自数据库候选。 */
    private Long itemId;
    /** 餐食所属数据源，用于前端卡片和数据源隔离校验。 */
    private SourceMode sourceType;
    /** 餐食名称，必须来自数据库候选。 */
    private String name;
    /** 推荐理由，由 RecommendAgent 或模板兜底生成。 */
    private String reason;
    /** Java 重排后的归一化分数，范围 0 到 1。 */
    private double matchScore;
    /** 餐食原始槽位，用于前端卡片和 Trace。 */
    private SlotBundle matchedSlots;
}




