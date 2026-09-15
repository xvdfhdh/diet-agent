package com.diet.controller.chat;

import com.diet.constants.DietConstants;
import com.diet.model.ChatRequest;
import com.diet.model.ChatResponse;
import com.diet.model.ChatStreamEvent;
import com.diet.service.orchestrator.DietOrchestratorService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

/**
 * 饮食推荐对话 HTTP 入口。
 * 本层只做参数透传，完整状态机由 {@link DietOrchestratorService#dietChat} 驱动。
 */
@RestController
@RequestMapping("/api/v1/diet")
public class DietChatController {

    /** 多 Agent 编排服务，注入后用于处理每轮对话。 */
    private final DietOrchestratorService orchestratorService;

    /** Spring 构造器注入 Orchestrator。 */
    public DietChatController(DietOrchestratorService orchestratorService) {
        this.orchestratorService = orchestratorService;
    }

    /**
     * POST /api/v1/diet/chat — 同步对话接口。
     * 接收用户消息，返回澄清追问或推荐结果（含餐食卡片）。
     */
    @PostMapping("/chat")
    public ChatResponse dietChat(
            // 从请求头 X-User-Id 读取用户 ID，缺省为 1 便于本地调试
            @RequestHeader(value = DietConstants.USER_ID, defaultValue = "1") Long userId,
            // 从请求体反序列化 ChatRequest（sessionId、message、sourceMode）
            @RequestBody ChatRequest request
    ) {
        // 委托 Orchestrator 执行完整状态机，直接返回 ChatResponse
        return orchestratorService.dietChat(userId, request);
    }

    /** SSE 对话接口：先推送流水线状态，再逐段推送最终文本，最后发送完整响应。 */
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter dietChatStream(
            @RequestHeader(value = DietConstants.USER_ID, defaultValue = "1") Long userId,
            @RequestBody ChatRequest request) {
        SseEmitter emitter = new SseEmitter(120_000L);
        CompletableFuture.runAsync(() -> {
            try {
                ChatResponse response = orchestratorService.dietChat(
                        userId, request, status -> send(emitter, ChatStreamEvent.status(status)));
                streamText(emitter, response.speechText());
                send(emitter, ChatStreamEvent.complete(response));
                emitter.complete();
            } catch (Exception error) {
                try {
                    send(emitter, ChatStreamEvent.error(error.getMessage() == null ? "生成推荐失败" : error.getMessage()));
                    emitter.complete();
                } catch (RuntimeException disconnected) {
                    emitter.completeWithError(error);
                }
            }
        });
        return emitter;
    }

    private void streamText(SseEmitter emitter, String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        int codePointOffset = 0;
        int codePointCount = text.codePointCount(0, text.length());
        while (codePointOffset < codePointCount) {
            int next = Math.min(codePointOffset + 12, codePointCount);
            int startIndex = text.offsetByCodePoints(0, codePointOffset);
            int endIndex = text.offsetByCodePoints(0, next);
            send(emitter, ChatStreamEvent.delta(text.substring(startIndex, endIndex)));
            codePointOffset = next;
        }
    }

    private void send(SseEmitter emitter, ChatStreamEvent event) {
        try {
            emitter.send(SseEmitter.event().name(event.type()).data(event));
        } catch (IOException error) {
            throw new IllegalStateException("SSE 连接已断开", error);
        }
    }
}
