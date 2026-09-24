package com.diet.service.agentic;

import com.diet.enums.AgentTaskType;
import com.diet.service.model.ModelConfigService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class AgentTaskRouterTest {
    private final AgentTaskRouter router = new AgentTaskRouter(mock(ModelConfigService.class));

    @Test
    void routesExplicitTasksWithoutCallingModel() {
        assertThat(router.route("帮我按照之前的每日饮食计划填写今日计划")).isEqualTo(AgentTaskType.PLAN);
        assertThat(router.route("推荐一道适合午餐的菜")).isEqualTo(AgentTaskType.RECOMMEND);
        assertThat(router.route("从计划同步购物清单")).isEqualTo(AgentTaskType.SHOPPING);
        assertThat(router.route("早餐已吃，饱腹感四分")).isEqualTo(AgentTaskType.CHECKIN);
        assertThat(router.route("取消收藏这道菜")).isEqualTo(AgentTaskType.FAVORITE);
        assertThat(router.route("记住我偏好清淡口味")).isEqualTo(AgentTaskType.PREFERENCE);
    }
}
