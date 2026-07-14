package com.diet.agent.factory;

import com.diet.agent.builder.*;
import io.agentscope.core.ReActAgent;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.experimental.Accessors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * newdiet 会话级 Agent 工厂。
 * 每个会话持有一套 Agent，避免不同用户的 Agent 内部记忆串话。
 */
@Component
public class AgentFactory {
    /** Agent 缓存最大容量，超过后按 LRU 淘汰最久未使用的会话。 */
    private static final int MAX_AGENT_SETS = 1000;

    /** IntentAgent 构建器。 */
    private final IntentAgentBuilder intentBuilder;

    /** ClarifyAgent 构建器。 */
    private final ClarifyAgentBuilder clarifyBuilder;

    /** RecommendResponseAgent 构建器，合并推荐理由与应答包装。 */
    private final RecommendResponseAgentBuilder recommendResponseBuilder;

    /** Prompt 版本，Prompt 升级后可以通过配置变更避免复用旧 Agent。 */
    private final String promptVersion;

    /** 会话级缓存，accessOrder=true 表示最近访问的元素排到末尾。 */
    private final Map<String, AgentSet> cache = Collections.synchronizedMap(
            new LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, AgentSet> eldest) {
                    return size() > MAX_AGENT_SETS;
                }
            }
    );

    /** 构造器注入全部 Builder 和 Prompt 版本。 */
    public AgentFactory(
            IntentAgentBuilder intentBuilder,
            ClarifyAgentBuilder clarifyBuilder,
            RecommendResponseAgentBuilder recommendResponseBuilder,
            @Value("${diet.prompt.version:v1}") String promptVersion
    ) {
        this.intentBuilder = intentBuilder;
        this.clarifyBuilder = clarifyBuilder;
        this.recommendResponseBuilder = recommendResponseBuilder;
        this.promptVersion = promptVersion;
    }

    /** 获取当前会话的一整套 Agent，不存在时按需创建。 */
    public AgentSet get(String sessionId) {
        return cache.computeIfAbsent(cacheKey(sessionId), ignored -> new AgentSet(
                intentBuilder.build(),
                clarifyBuilder.build(),
                recommendResponseBuilder.build()
        ));
    }

    /** 会话结束时释放该会话的 Agent 集合。 */
    public void remove(String sessionId) {
        cache.remove(cacheKey(sessionId));
    }

    /** 生成包含 Prompt 版本的缓存键。 */
    private String cacheKey(String sessionId) {
        return sessionId + "::" + promptVersion;
    }

    /**
     * 一个会话内可用的全部 Worker Agent。
     * Worker 只产出结构化结果，不直接读写 SessionState。
     */
    @Data
    @Accessors(fluent = true)
    @AllArgsConstructor
    public static class AgentSet {
        private ReActAgent intent;
        private ReActAgent clarify;
        private ReActAgent recommendResponse;
    }
}