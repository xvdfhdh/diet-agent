package com.diet.service.agentic;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AgentMutationPolicyTest {
    private final AgentMutationPolicy policy = new AgentMutationPolicy();

    @Test
    void vaguePraiseCannotWritePlan() {
        assertThat(policy.mutationRequested("这道不错")).isFalse();
        assertThat(policy.mayAddPlan("把它加入明天午餐计划")).isTrue();
        assertThat(policy.mayAddPlan("帮我按照之前的每日饮食计划，填写今日计划")).isTrue();
        assertThat(policy.mayAddPlan("沿用昨天的早餐和午餐到今天")).isTrue();
    }

    @Test
    void shoppingWriteRequiresExplicitListIntent() {
        assertThat(policy.maySyncShopping("帮我看看本周买什么")).isFalse();
        assertThat(policy.maySyncShopping("同步本周购物清单")).isTrue();
    }
}
