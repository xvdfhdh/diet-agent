package com.diet.service.agentic;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AgentMutationPolicyTest {
    private final AgentMutationPolicy policy = new AgentMutationPolicy();

    @Test
    void vaguePraiseCannotWritePlan() {
        assertThat(policy.mutationRequested("这道不错")).isFalse();
        assertThat(policy.mayAddPlan("把它加入明天午餐计划")).isTrue();
    }

    @Test
    void shoppingWriteRequiresExplicitListIntent() {
        assertThat(policy.maySyncShopping("帮我看看本周买什么")).isFalse();
        assertThat(policy.maySyncShopping("同步本周购物清单")).isTrue();
    }
}
