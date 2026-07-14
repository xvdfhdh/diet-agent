package com.diet.service.clarify;

import com.diet.agent.factory.AgentFactory;
import com.diet.model.ClarifyResult;
import com.diet.model.SlotBundle;
import com.diet.service.trace.AgentTraceService;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.message.Msg;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.util.List;

/**
 * ClarifyAgent 调用服务。
 * 规则层（ClarifyRuleService）先判 ASK/READY；仅 ASK 时才调 LLM 生成自然语言追问。
 */
@Service
public class ClarifyAgentService {

    /** 按 sessionId 提供 ClarifyAgent 实例的工厂。 */
    private final AgentFactory agentFactory;

    /** 澄清规则服务，计算 missingSlots 决定是否需要追问。 */
    private final ClarifyRuleService clarifyRuleService;

    /** 链路追踪服务，callAgent 内部记录 AGENT_CALL 事件。 */
    private final AgentTraceService agentTraceService;

    /** ClarifyAgent 使用的轻量模型名，来自配置 diet.llm.light-model。 */
    private final String modelName;

    /** 构造器注入依赖。 */
    public ClarifyAgentService(
            AgentFactory agentFactory,
            ClarifyRuleService clarifyRuleService,
            AgentTraceService agentTraceService,
            @Value("${diet.llm.light-model:qwen-turbo}") String modelName
    ) {
        this.agentFactory = agentFactory;
        this.clarifyRuleService = clarifyRuleService;
        this.agentTraceService = agentTraceService;
        this.modelName = modelName;
    }

    /**
     * 根据槽位决定是否追问，需要时生成追问文案。
     * 由 Orchestrator#handleRecommendation 调用，返回 ClarifyResult（ASK 或 READY）。
     */
    public ClarifyResult decide(String sessionId, String userInput, SlotBundle slots) {
        // 用 Java 规则计算缺失的关键槽位（mealTime、healthGoal 等）
        List<String> missingSlots = clarifyRuleService.missingSlots(slots);
        // 无缺失槽位 → 槽位足够，直接 READY，不调用 LLM
        if (missingSlots.isEmpty()) {
            return ClarifyResult.ready();
        }
        try {
            // 从 AgentFactory 获取当前 session 的 ClarifyAgent 实例
            ReActAgent agent = agentFactory.get(sessionId).clarify();
            // 清空 Agent 内存，避免跨轮污染
            agent.getMemory().clear();
            // 调用 Agent：内部走 agentTraceService.callAgent，记录 AGENT_CALL（ClarifyAgent + light-model）
            Msg response = agentTraceService.callAgent(sessionId, "ClarifyAgent", modelName, agent, buildUserPrompt(userInput, slots, missingSlots));
            // 提取 Agent 返回的追问文案并 trim
            String question = response.getTextContent() == null ? "" : response.getTextContent().trim();
            // LLM 返回空文本时，用 ClarifyRule 模板追问兜底
            if (question.isBlank()) {
                question = clarifyRuleService.fallbackQuestion(missingSlots);
            }
            // 返回 ASK 结果：携带追问文案和缺失槽位列表
            return ClarifyResult.ask(question, missingSlots);
        } catch (Exception ignored) {
            // LLM 异常时用模板追问兜底，保证用户一定能看到问题
            return ClarifyResult.ask(clarifyRuleService.fallbackQuestion(missingSlots), missingSlots);
        }
    }

    /** 构造 ClarifyAgent 的输入 prompt。 */
    private String buildUserPrompt(String userInput, SlotBundle slots, List<String> missingSlots) {
        return """
                用户原话：%s
                已知信息：%s
                缺失字段：%s
                """.formatted(userInput, slots, missingSlots);
    }
}