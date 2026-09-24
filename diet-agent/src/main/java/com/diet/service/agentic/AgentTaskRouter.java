package com.diet.service.agentic;

import com.diet.enums.AgentTaskType;
import com.diet.service.model.ModelConfigService;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.memory.InMemoryMemory;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Locale;

@Service
public class AgentTaskRouter {
    private final ModelConfigService models;

    public AgentTaskRouter(ModelConfigService models) { this.models = models; }

    public AgentTaskType route(String input) {
        String text = input == null ? "" : input.trim().toLowerCase(Locale.ROOT);
        if (contains(text, "购物", "采购", "买菜", "清单", "食材买")) return AgentTaskType.SHOPPING;
        if (contains(text, "打卡", "已吃", "吃完", "没吃", "跳过", "饱腹", "吃不饱")) return AgentTaskType.CHECKIN;
        if (contains(text, "收藏", "取消收藏")) return AgentTaskType.FAVORITE;
        if (contains(text, "记住", "偏好", "忌口", "不爱吃", "以后不要", "忘掉")) return AgentTaskType.PREFERENCE;
        if (contains(text, "推荐", "吃什么", "想吃", "来一道", "换一道", "做什么菜")) return AgentTaskType.RECOMMEND;
        if (contains(text, "计划", "安排一周", "每日饮食", "照搬", "沿用", "复制到")
                || contains(text, "填写今天", "填入今天", "填充今天", "安排今天")) return AgentTaskType.PLAN;
        return routeAmbiguous(text);
    }

    private AgentTaskType routeAmbiguous(String text) {
        if (text.isBlank()) return AgentTaskType.GENERAL;
        try {
            ReActAgent agent = ReActAgent.builder().name("diet_agent_task_router")
                    .model(models.lightModel())
                    .sysPrompt("将用户请求分类为 RECOMMEND、PLAN、SHOPPING、CHECKIN、FAVORITE、PREFERENCE、GENERAL；只回复枚举值。")
                    .memory(new InMemoryMemory()).build();
            Msg response = agent.call(Msg.builder().role(MsgRole.USER).textContent(text).build())
                    .block(Duration.ofSeconds(5));
            String value = response == null ? "" : response.getTextContent();
            for (AgentTaskType type : AgentTaskType.values())
                if (value != null && value.toUpperCase(Locale.ROOT).contains(type.name())) return type;
        } catch (Exception ignored) {
            // 路由模型是增强项；失败时由主 Agent 的 GENERAL 工具集继续处理。
        }
        return AgentTaskType.GENERAL;
    }

    private boolean contains(String text, String... values) {
        for (String value : values) if (text.contains(value)) return true;
        return false;
    }
}
