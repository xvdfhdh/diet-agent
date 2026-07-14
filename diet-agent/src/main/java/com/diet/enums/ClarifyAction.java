package com.diet.enums;

/**
 * 澄清节点的处理结果。
 * ASK 表示需要向用户追问，READY 表示信息足够进入推荐链路。
 */
public enum ClarifyAction {
    /** 信息不足，需要返回一个 clarify SSE 事件。 */
    ASK,

    /** 信息足够，可以继续检索和推荐。 */
    READY
}