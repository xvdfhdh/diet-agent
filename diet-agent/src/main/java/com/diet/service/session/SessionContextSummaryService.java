package com.diet.service.session;

import com.diet.mapper.SessionMapper;
import com.diet.model.SessionMessageRow;
import com.diet.model.SessionRow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/** Maintains a compact, non-blocking conversation summary for long Agent sessions. */
@Service
public class SessionContextSummaryService {
    private static final Logger log = LoggerFactory.getLogger(SessionContextSummaryService.class);
    private static final int SUMMARY_THRESHOLD = 12;
    private static final int RAW_TAIL_SIZE = 8;
    private static final int SUMMARY_WINDOW = 48;
    private static final int MAX_SUMMARY_LENGTH = 2400;
    private final SessionMapper mapper;
    private final Set<String> inFlight = ConcurrentHashMap.newKeySet();

    public SessionContextSummaryService(SessionMapper mapper) { this.mapper = mapper; }

    public void schedule(Long userId, String sessionId) {
        if (userId == null || sessionId == null || sessionId.isBlank()) return;
        Runnable submit = () -> {
            String key = userId + "::" + sessionId;
            if (!inFlight.add(key)) return;
            CompletableFuture.runAsync(() -> refresh(userId, sessionId))
                    .whenComplete((ignored, error) -> {
                        inFlight.remove(key);
                        if (error != null) log.debug("Conversation summary refresh skipped: sessionId={}", sessionId, error);
                    });
        };
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { submit.run(); }
            });
        } else submit.run();
    }

    void refresh(Long userId, String sessionId) {
        if (mapper.countMessages(sessionId, userId) <= SUMMARY_THRESHOLD) return;
        SessionRow session = mapper.findById(sessionId, userId);
        if (session == null) return;
        List<SessionMessageRow> descending = mapper.listRecentMessages(sessionId, userId, SUMMARY_WINDOW);
        if (descending.size() <= RAW_TAIL_SIZE) return;
        Long newestSummarized = descending.get(RAW_TAIL_SIZE).getId();
        if (session.getSummaryThroughMessageId() != null
                && session.getSummaryThroughMessageId() >= newestSummarized) return;
        List<SessionMessageRow> summarized = new ArrayList<>(descending.subList(RAW_TAIL_SIZE, descending.size()));
        Collections.reverse(summarized);
        StringBuilder value = new StringBuilder("较早对话摘要：\n");
        for (SessionMessageRow message : summarized) {
            String content = normalize(message.getContent());
            if (content.isBlank()) continue;
            String line = ("user".equalsIgnoreCase(message.getRole()) ? "用户：" : "助手：") + content + "\n";
            if (value.length() + line.length() > MAX_SUMMARY_LENGTH) break;
            value.append(line);
        }
        mapper.updateContextSummary(sessionId, userId, value.toString().trim(), newestSummarized);
    }

    private String normalize(String value) {
        if (value == null) return "";
        String compact = value.replace('\r', ' ').replace('\n', ' ').replaceAll("\\s+", " ").trim();
        return compact.length() <= 160 ? compact : compact.substring(0, 160) + "…";
    }
}
