package com.diet.service.agentic;

import com.diet.service.model.ModelConfigChangedEvent;
import com.diet.service.model.ModelConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class AgentCircuitBreaker {
    private static final int FAILURE_THRESHOLD = 3;
    private static final Duration OPEN_DURATION = Duration.ofSeconds(60);

    private final ModelConfigService models;
    private final ConcurrentHashMap<String, State> states = new ConcurrentHashMap<>();

    public AgentCircuitBreaker() { this.models = null; }

    @Autowired
    public AgentCircuitBreaker(ModelConfigService models) { this.models = models; }

    public synchronized boolean isOpen() {
        State state = states.get(key());
        if (state == null || state.openUntil == null) return false;
        if (Instant.now().isBefore(state.openUntil)) return true;
        states.remove(key());
        return false;
    }

    public synchronized void recordInfrastructureFailure() {
        State state = states.computeIfAbsent(key(), ignored -> new State());
        state.consecutiveFailures++;
        if (state.consecutiveFailures >= FAILURE_THRESHOLD) {
            state.openUntil = Instant.now().plus(OPEN_DURATION);
        }
    }

    public synchronized void recordSuccess() {
        reset();
    }

    @EventListener
    public synchronized void onModelConfigChanged(ModelConfigChangedEvent ignored) {
        states.clear();
    }

    private void reset() {
        states.remove(key());
    }

    private String key() {
        if (models == null) return "test::default";
        ModelConfigService.Snapshot config = models.current();
        return config.providerId() + "::" + config.mainModel();
    }

    private static final class State {
        private int consecutiveFailures;
        private Instant openUntil;
    }
}
