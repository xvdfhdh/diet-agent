package com.diet.service.model;

import com.diet.exception.DietException;
import com.diet.mapper.ModelConfigMapper;
import com.diet.model.ModelConfigRequest;
import com.diet.model.ModelConfigResponse;
import com.diet.model.ModelConfigRow;
import com.diet.model.ModelConnectionTestResponse;
import com.diet.model.ModelProviderTemplate;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.memory.InMemoryMemory;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.model.Model;
import io.agentscope.core.model.OpenAIChatModel;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import io.agentscope.core.tool.Toolkit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class ModelConfigService {
    private static final List<ModelProviderTemplate> TEMPLATES = List.of(
            new ModelProviderTemplate("aliyun", "阿里云百炼", "https://dashscope.aliyuncs.com/compatible-mode/v1", "/chat/completions", "qwen-max", "qwen-turbo", "https://help.aliyun.com/zh/model-studio/compatibility-of-openai-with-dashscope"),
            new ModelProviderTemplate("openai", "OpenAI", "https://api.openai.com/v1", "/chat/completions", "gpt-4.1", "gpt-4.1-mini", "https://platform.openai.com/docs"),
            new ModelProviderTemplate("deepseek", "DeepSeek", "https://api.deepseek.com", "/chat/completions", "deepseek-v4-pro", "deepseek-v4-flash", "https://api-docs.deepseek.com/"),
            new ModelProviderTemplate("zhipu", "智谱 AI", "https://open.bigmodel.cn/api/paas/v4", "/chat/completions", "glm-5.2", "glm-4.5-flash", "https://docs.bigmodel.cn/cn/guide/develop/openai/introduction"),
            new ModelProviderTemplate("moonshot", "月之暗面", "https://api.moonshot.cn/v1", "/chat/completions", "kimi-k2.5", "moonshot-v1-8k", "https://platform.moonshot.cn/docs"),
            new ModelProviderTemplate("siliconflow", "硅基流动", "https://api.siliconflow.cn/v1", "/chat/completions", "deepseek-ai/DeepSeek-V3.2", "Qwen/Qwen3.6-27B", "https://docs.siliconflow.cn/docs/userguide/quickstart"),
            new ModelProviderTemplate("custom", "自定义", "", "/chat/completions", "", "", "")
    );

    private final ModelConfigMapper mapper;
    private final ApplicationEventPublisher eventPublisher;
    private final AtomicLong revision = new AtomicLong(1);
    private final Snapshot defaults;
    private volatile Snapshot cached;

    public ModelConfigService(
            ModelConfigMapper mapper,
            ApplicationEventPublisher eventPublisher,
            @Value("${agentscope.dashscope.api-key:}") String defaultApiKey,
            @Value("${diet.llm.base-url:https://dashscope.aliyuncs.com/compatible-mode/v1}") String defaultBaseUrl,
            @Value("${diet.llm.endpoint-path:/chat/completions}") String defaultEndpointPath,
            @Value("${diet.llm.main-model:qwen-max}") String defaultMainModel,
            @Value("${diet.llm.light-model:qwen-turbo}") String defaultLightModel
    ) {
        this.mapper = mapper;
        this.eventPublisher = eventPublisher;
        this.defaults = new Snapshot("aliyun", "阿里云百炼", defaultBaseUrl, defaultEndpointPath,
                defaultMainModel, defaultLightModel, defaultApiKey == null ? "" : defaultApiKey.trim());
    }

    public Snapshot current() {
        Snapshot value = cached;
        if (value != null) {
            return value;
        }
        synchronized (this) {
            if (cached == null) {
                cached = load();
            }
            return cached;
        }
    }

    public ModelConfigResponse view() {
        return toResponse(current());
    }

    public long revision() {
        return revision.get();
    }

    public Model mainModel() {
        Snapshot config = current();
        return createModel(config, config.mainModel());
    }

    public Model lightModel() {
        Snapshot config = current();
        return createModel(config, config.lightModel());
    }

    @Transactional
    public ModelConfigResponse save(ModelConfigRequest request) {
        Snapshot next = fromRequest(request, current(), true);
        ModelConfigRow row = new ModelConfigRow();
        row.setId(1L);
        row.setProviderId(next.providerId());
        row.setDisplayName(next.displayName());
        row.setBaseUrl(next.baseUrl());
        row.setEndpointPath(next.endpointPath());
        row.setMainModel(next.mainModel());
        row.setLightModel(next.lightModel());
        row.setApiKey(next.apiKey());
        mapper.upsert(row);
        cached = next;
        long nextRevision = revision.incrementAndGet();
        eventPublisher.publishEvent(new ModelConfigChangedEvent(nextRevision));
        return toResponse(next);
    }

    public ModelConnectionTestResponse test(ModelConfigRequest request) {
        Snapshot candidate = fromRequest(request, current(), true);
        long startedAt = System.nanoTime();
        try {
            ReActAgent probe = ReActAgent.builder()
                    .name("diet_model_connection_probe")
                    .model(createModel(candidate, candidate.lightModel()))
                    .sysPrompt("你是连接测试助手。收到消息后只回复 OK。")
                    .memory(new InMemoryMemory())
                    .build();
            Msg response = probe.call(Msg.builder().role(MsgRole.USER).textContent("只回复 OK").build()).block();
            if (response == null || response.getTextContent() == null || response.getTextContent().isBlank()) {
                return new ModelConnectionTestResponse(false, elapsedMs(startedAt), "接口已响应，但没有返回文本", null);
            }
            boolean toolsSupported = testToolCalling(candidate);
            return new ModelConnectionTestResponse(true, elapsedMs(startedAt),
                    toolsSupported ? "文本调用正常，工具调用正常" : "文本调用正常，但工具调用未通过",
                    toolsSupported);
        } catch (Exception error) {
            String detail = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
            return new ModelConnectionTestResponse(false, elapsedMs(startedAt),
                    "连接失败：" + abbreviate(detail, 220), null);
        }
    }

    private boolean testToolCalling(Snapshot candidate) {
        try {
            AtomicBoolean called = new AtomicBoolean();
            Toolkit toolkit = new Toolkit();
            toolkit.registerTool(new ToolCapabilityProbe(called));
            ReActAgent probe = ReActAgent.builder()
                    .name("diet_model_tool_probe")
                    .model(createModel(candidate, candidate.mainModel()))
                    .sysPrompt("必须调用 diet_echo 工具并传入 ping；工具返回后只回复 OK。")
                    .toolkit(toolkit)
                    .memory(new InMemoryMemory())
                    .maxIters(3)
                    .build();
            probe.call(Msg.builder().role(MsgRole.USER).textContent("请调用工具完成测试").build()).block();
            return called.get();
        } catch (Exception ignored) {
            return false;
        }
    }

    public static final class ToolCapabilityProbe {
        private final AtomicBoolean called;

        private ToolCapabilityProbe(AtomicBoolean called) {
            this.called = called;
        }

        @Tool(name = "diet_echo", description = "连接测试工具，原样返回输入")
        public String echo(@ToolParam(name = "value", required = true, description = "必须传 ping") String value) {
            called.set(true);
            return value;
        }
    }

    private Snapshot load() {
        try {
            ModelConfigRow row = mapper.findCurrent();
            if (row == null) {
                return defaults;
            }
            return new Snapshot(row.getProviderId(), row.getDisplayName(), row.getBaseUrl(), row.getEndpointPath(),
                    row.getMainModel(), row.getLightModel(), row.getApiKey() == null ? "" : row.getApiKey());
        } catch (RuntimeException ignored) {
            // 兼容尚未执行新版 SQL 的旧数据库；保存设置前仍需创建 diet_model_config 表。
            return defaults;
        }
    }

    private Snapshot fromRequest(ModelConfigRequest request, Snapshot existing, boolean requireKey) {
        if (request == null) {
            throw new DietException("模型配置不能为空");
        }
        String apiKey = clean(request.apiKey());
        if (apiKey.isBlank()) {
            apiKey = existing.apiKey();
        }
        Snapshot result = new Snapshot(
                defaultIfBlank(request.providerId(), "custom").toLowerCase(Locale.ROOT),
                defaultIfBlank(request.displayName(), "自定义服务"),
                clean(request.baseUrl()),
                defaultIfBlank(request.endpointPath(), "/chat/completions"),
                clean(request.mainModel()),
                clean(request.lightModel()),
                apiKey
        );
        validate(result, requireKey);
        return result;
    }

    private void validate(Snapshot config, boolean requireKey) {
        if (config.baseUrl().isBlank() || config.mainModel().isBlank() || config.lightModel().isBlank()) {
            throw new DietException("接口地址、主模型和轻量模型不能为空");
        }
        if (requireKey && config.apiKey().isBlank()) {
            throw new DietException("API Key 不能为空");
        }
        URI uri;
        try {
            uri = URI.create(config.baseUrl());
        } catch (Exception error) {
            throw new DietException("接口地址格式不正确");
        }
        if (uri.getHost() == null || !("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))) {
            throw new DietException("接口地址必须是 HTTP 或 HTTPS URL");
        }
        if (!config.endpointPath().startsWith("/")) {
            throw new DietException("接口路径必须以 / 开头");
        }
    }

    private Model createModel(Snapshot config, String modelName) {
        return OpenAIChatModel.builder()
                .apiKey(config.apiKey())
                .baseUrl(config.baseUrl())
                .endpointPath(config.endpointPath())
                .modelName(modelName)
                .stream(false)
                .build();
    }

    private ModelConfigResponse toResponse(Snapshot config) {
        return new ModelConfigResponse(
                config.providerId(), config.displayName(), config.baseUrl(), config.endpointPath(),
                config.mainModel(), config.lightModel(), !config.apiKey().isBlank(), mask(config.apiKey()), TEMPLATES
        );
    }

    private String mask(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String suffix = value.length() <= 4 ? "" : value.substring(value.length() - 4);
        return "••••••••" + suffix;
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private String defaultIfBlank(String value, String fallback) {
        String cleaned = clean(value);
        return cleaned.isBlank() ? fallback : cleaned;
    }

    private long elapsedMs(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }

    private String abbreviate(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength) + "…";
    }

    public record Snapshot(
            String providerId,
            String displayName,
            String baseUrl,
            String endpointPath,
            String mainModel,
            String lightModel,
            String apiKey
    ) {
    }
}
