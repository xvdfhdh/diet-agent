package com.diet.enums;

/**
 * Guard 对回复风险的分级结果。
 */
public enum RiskLevel {
    /** 普通饮食推荐风险低，可以继续输出。 */
    LOW,

    /** 存在医疗、治疗、极端节食等高风险，必须保守拦截，不可降级放行。 */
    HIGH
}