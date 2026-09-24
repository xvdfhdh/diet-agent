package com.diet.service.agentic;

import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Locale;

@Component
public class AgentMutationPolicy {
    public boolean mayAddPlan(String input) {
        String text = normalize(input);
        return containsAny(text, "加入", "添加", "安排", "放到", "放进",
                "填写", "填入", "填充", "制定", "生成", "创建", "沿用", "照搬", "复制", "套用")
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

    public boolean mayModifyShopping(String input) {
        String text = normalize(input);
        return containsAny(text, "购物", "采购", "清单", "买菜")
                && containsAny(text, "添加", "加入", "勾选", "买了", "修改", "更新", "删除", "移除", "同步", "生成", "整理");
    }

    public boolean mayCheckIn(String input) {
        String text = normalize(input);
        return containsAny(text, "已吃", "吃完", "打卡", "跳过", "没吃", "不吃", "吃了", "饱腹", "吃不饱");
    }

    public boolean mayFavorite(String input) {
        return containsAny(normalize(input), "收藏", "取消收藏", "不再收藏");
    }

    public boolean mayModifyPreference(String input) {
        String text = normalize(input);
        return containsAny(text, "记住", "偏好", "喜欢", "不喜欢", "不吃", "忌口", "以后", "忘掉", "移除")
                && containsAny(text, "口味", "菜系", "清淡", "辣", "健康", "方便", "快速", "喜欢", "不吃", "忌口", "不要");
    }

    public boolean mutationRequested(String input) {
        return mayAddPlan(input) || mayReplacePlan(input) || maySyncShopping(input)
                || mayModifyShopping(input) || mayCheckIn(input) || mayFavorite(input) || mayModifyPreference(input);
    }

    private boolean containsAny(String text, String... values) {
        return List.of(values).stream().anyMatch(text::contains);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
