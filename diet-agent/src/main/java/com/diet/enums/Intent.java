package com.diet.enums;

/**
 * 饮食助手的一轮用户输入意图
 * 这个枚举是 Orchestrator 状态机的分支依据
 */
public enum Intent {
    /** 用户正在请求餐食推荐，例如“晚饭推荐清淡一点的”。 */
    MEAL_RECOMMENDATION,

    /** 用户有就餐意向，但当前信息不足，需要先追问关键槽位。 */
    CLARIFY_NEEDED,

    /** 用户基于上一轮推荐要求换一批、清淡点或快一点。 */
    MEAL_ADJUST,

    /** 用户要求早餐、午餐、晚餐等多餐规划。 */
    MEAL_PLAN,

    /** 用户问题涉及医疗诊断、治疗承诺、极端节食等健康风险。 */
    HEALTH_RISK,

    /** 用户输入与饮食无关内容。 */
    OTHER
}