package com.diet.enums;

/**
 * 会话状态阶段
 * Orchestrator 通过状态阶段判断当前会话是否处于澄清、推荐或规划流程中。
 */
public enum SessionPhase {
    /** 会话刚创建，还没有进入任何业务流程。 */
    START,

    /** 系统正在向用户追问缺失信息。 */
    CLARIFY,

    /** 系统已经进入推荐链路，并保存过上一轮推荐结果。 */
    RECOMMEND,

    /** 系统正在处理多餐规划。 */
    PLAN
}