package com.diet.agent.builder;

import com.diet.agent.loader.PromptLoader;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.memory.InMemoryMemory;
import io.agentscope.core.model.Model;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * RecommendResponseAgent 构建器。
 */
@Component
public class RecommendResponseAgentBuilder {
    /** 主模型用于生成推荐理由和最终口语回复。 */
    private final Model mainModel;

    /** PromptLoader 用于加载 recommend-response.txt。 */
    private final PromptLoader promptLoader;

    /** 构造器注入模型和 PromptLoader。 */
    public RecommendResponseAgentBuilder(@Qualifier("DietMainChatModel") Model mainModel, PromptLoader promptLoader) {
        this.mainModel = mainModel;
        this.promptLoader = promptLoader;
    }

    /** 构建一个新的推荐应答 Agent。 */
    public ReActAgent build() {
        return ReActAgent.builder()
                .name("diet_recommend_response_agent")
                .model(mainModel)
                .sysPrompt(promptLoader.load("diet/prompts/recommend-response.txt"))
                .memory(new InMemoryMemory())
                .build();
    }
}