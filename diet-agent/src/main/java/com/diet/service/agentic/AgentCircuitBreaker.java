package com.diet.service.agentic;

import com.diet.service.model.ModelConfigChangedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

@Component
public class AgentCircuitBreaker {
    private static final int FAILURE_THRESHOLD = 3;
    private static final Duration OPEN_DURATION = Duration.ofSeconds(60);

    private int consecutiveFailures;
    private Instant openUntil;

    public synchronized boolean isOpen() {
        if (openUntil == null) return false;
        if (Instant.now().isBefore(openUntil)) return true;
        reset();
        return false;
    }

    public synchronized void recordInfrastructureFailure() {
        consecutiveFailures++;
        if (consecutiveFailures >= FAILURE_THRESHOLD) {
            openUntil = Instant.now().plus(OPEN_DURATION);
        }
    }

    public synchronized void recordSuccess() {
        reset();
    }

    @EventListener
    public synchronized void onModelConfigChanged(ModelConfigChangedEvent ignored) {
        reset();
    }

    private void reset() {
        consecutiveFailures = 0;
        openUntil = null;
    }
}
