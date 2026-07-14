package com.diet.model;

import com.diet.enums.Intent;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 短期记忆中的一轮摘要。
 * 它用于传给 IntentAgent 判断“换一批”等上下文相关输入，而不是作为 Agent 内部隐式记忆。
 */
@Data
@Accessors(fluent = true)
@AllArgsConstructor
public class ConversationTurn {
    /** user 或 assistant。 */
    private String role;
    /** assistant 轮次对应的意图，user 轮次可以为空。 */
    private Intent intent;
    /** 为节省 token 而保留的短文本摘要。 */
    private String summary;
    /** 写入时间戳，用于调试和后续替换为 Redis/DB 时排序。 */
    private long timestamp;
}