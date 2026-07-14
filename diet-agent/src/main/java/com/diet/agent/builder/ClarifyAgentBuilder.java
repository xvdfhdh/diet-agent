package com.diet.agent.builder;

import com.diet.agent.loader.PromptLoader;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.memory.InMemoryMemory;
import io.agentscope.core.model.Model;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * ClarifyAgent 构建器。
 * 是否需要追问由 Java 规则判断，本 Agent 只把缺失字段转换成自然中文问题。
 */
@Component
public class ClarifyAgentBuilder {
    /** 轻量模型足够完成一句追问生成。 */
    private final Model lightModel;

    /** PromptLoader 用于加载现有 clarify.txt。 */
    private final PromptLoader promptLoader;

    /** 构造器注入模型和 PromptLoader。 */
    public ClarifyAgentBuilder(@Qualifier("DietLightChatModel") Model lightModel, PromptLoader promptLoader) {
        this.lightModel = lightModel;
        this.promptLoader = promptLoader;
    }

    /** 构建一个新的澄清 Agent。 */
    public ReActAgent build() {
        return ReActAgent.builder()
                .name("diet_clarify_agent")
                .model(lightModel)
                .sysPrompt(promptLoader.load("diet/prompts/clarify.txt"))
                .memory(new InMemoryMemory())
                .build();
    }
}