package com.diet.service.agentic;

import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Locale;

@Component
public class AgentMutationPolicy {
    public boolean mayAddPlan(String input) {
        String text = normalize(input);
        return containsAny(text, "加入", "添加", "安排", "放到", "放进")
                && containsAny(text, "计划", "早餐", "午餐", "晚餐", "加餐", "今天", "明天", "周一", "周二", "周三", "周四", "周五", "周六", "周日");
    }

    public boolean mayReplacePlan(String input) {
        String text = normalize(input);
        return containsAny(text, "替换", "换掉", "换一道", "换一个")
                && containsAny(text, "计划", "早餐", "午餐", "晚餐", "加餐", "这餐");
    }

    public boolean maySyncShopping(String input) {
        String text = normalize(input);
        return containsAny(text, "同步", "生成", "更新", "整理")
                && containsAny(text, "购物清单", "采购清单", "买菜清单");
    }

    public boolean mutationRequested(String input) {
        return mayAddPlan(input) || mayReplacePlan(input) || maySyncShopping(input);
    }

    private boolean containsAny(String text, String... values) {
        return List.of(values).stream().anyMatch(text::contains);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
