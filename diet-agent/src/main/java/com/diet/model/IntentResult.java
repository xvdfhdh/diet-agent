package com.diet.model;

import com.diet.enums.Intent;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * IntentAgent 的结构化输出。
 * Orchestrator 会先校验该结果，再根据历史上下文做二次矫正。
 */
@Data
@Accessors(fluent = true)
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
@AllArgsConstructor
public class IntentResult {
    /** 当前用户输入的意图。 */
    private Intent intent;
    /** 当前输入抽取出的标准 7 槽位。 */
    private SlotBundle slots;
    /** LLM 对分类结果的置信度，规则兜底时通常较低。 */
    private double confidence;

    /** 构造一个保守的澄清结果，用于 LLM 失败或输出不可解析时兜底。 */
    public static IntentResult clarify(SlotBundle slots) {
        return new IntentResult(Intent.CLARIFY_NEEDED, slots == null ? SlotBundle.empty() : slots, 0.2);
    }
}




