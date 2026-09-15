package com.diet.agent.builder;

import com.diet.agent.loader.PromptLoader;
import com.diet.service.model.ModelConfigService;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.memory.InMemoryMemory;
import org.springframework.stereotype.Component;

@Component
public class EvaluationJudgeAgentBuilder {
    private final ModelConfigService modelConfigService;
    private final PromptLoader promptLoader;

    public EvaluationJudgeAgentBuilder(ModelConfigService modelConfigService, PromptLoader promptLoader) {
        this.modelConfigService = modelConfigService;
        this.promptLoader = promptLoader;
    }

    public ReActAgent build() {
        return ReActAgent.builder()
                .name("diet_evaluation_judge_agent")
                .model(modelConfigService.lightModel())
                .sysPrompt(promptLoader.load("diet/prompts/evaluation-judge.txt"))
                .memory(new InMemoryMemory())
                .build();
    }
}
