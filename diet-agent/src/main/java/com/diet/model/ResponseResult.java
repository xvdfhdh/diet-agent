package com.diet.model;

import java.util.List;

import com.diet.model.MealResponse;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 编排层最终输出的应答结构。
 * 该对象会被 Orchestrator 拆成 token、recommendations、done 等 SSE 事件。
 */
@Data
@Accessors(fluent = true)
@AllArgsConstructor
public class ResponseResult {
    /** 面向用户展示或流式输出的自然语言回答。 */
    private String speechText;
    /** 前端推荐卡片数据，必须来自推荐候选。 */
    private List<MealResponse> displayBlocks;
    /** 前端下一步动作，默认 WAIT_USER。 */
    private String nextAction;

    /** 创建一个只有文本、没有推荐卡片的响应。 */
    public static ResponseResult textOnly(String text) {
        return new ResponseResult(text, List.of(), "WAIT_USER");
    }
}