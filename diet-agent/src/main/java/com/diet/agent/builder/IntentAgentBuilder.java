package com.diet.agent.builder;

import com.diet.agent.loader.PromptLoader;
import com.diet.service.model.ModelConfigService;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.memory.InMemoryMemory;
import org.springframework.stereotype.Component;

/**
 * IntentAgent 构建器。
 * 该 Agent 负责意图分类和槽位归一，使用轻量模型以降低延迟。
 */
@Component
public class IntentAgentBuilder {
    /** 轻量模型用于分类和 JSON 抽取任务。 */
    private final ModelConfigService modelConfigService;

    /** PromptLoader 用于加载现有 intent.txt。 */
    private final PromptLoader promptLoader;

    /** 构造器注入模型和 PromptLoader。 */
    public IntentAgentBuilder(ModelConfigService modelConfigService, PromptLoader promptLoader) {
        this.modelConfigService = modelConfigService;
        this.promptLoader = promptLoader;
    }

    /** 构建一个新的 ReActAgent 实例，实例内部记忆只作为临时容器使用。 */
    public ReActAgent build() {
        return ReActAgent.builder()
                .name("diet_intent_agent")
                .model(modelConfigService.lightModel())
                .sysPrompt(promptLoader.load("diet/prompts/intent.txt"))
                .memory(new InMemoryMemory())
                .build();
    }
}
