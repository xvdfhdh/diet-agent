package com.diet.model;

import java.util.List;

import com.diet.enums.ClarifyAction;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 澄清节点结构化输出。
 * 规则层决定 ASK/READY，LLM 只负责把缺失槽位包装成自然追问。
 */
@Data
@Accessors(fluent = true)
@AllArgsConstructor
public class ClarifyResult {
    /** ASK 表示需要追问，READY 表示可以继续推荐。 */
    private ClarifyAction action;
    /** 需要追问时返回给用户的一句话。 */
    private String questionToAsk;
    /** 当前仍缺失的槽位名，便于 Trace 与后续评估。 */
    private List<String> missingSlots;

    /** 创建 READY 结果。 */
    public static ClarifyResult ready() {
        return new ClarifyResult(ClarifyAction.READY, null, List.of());
    }

    /** 创建 ASK 结果。 */
    public static ClarifyResult ask(String questionToAsk, List<String> missingSlots) {
        return new ClarifyResult(ClarifyAction.ASK, questionToAsk, missingSlots == null ? List.of() : List.copyOf(missingSlots));
    }
}




