package com.diet.service.agentic;

import com.diet.enums.Intent;
import com.diet.enums.SessionPhase;
import com.diet.model.*;
import com.diet.service.history.RecommendationHistoryService;
import com.diet.service.memory.UserMemoryService;
import com.diet.service.session.SessionService;
import com.diet.service.session.SessionStateService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AgentResultCommitService {
    private final AgentMutationCommitService mutationCommitService;
    private final SessionStateService sessionStateService;
    private final SessionService sessionService;
    private final RecommendationHistoryService historyService;
    private final UserMemoryService memoryService;

    public AgentResultCommitService(AgentMutationCommitService mutationCommitService,
                                    SessionStateService sessionStateService, SessionService sessionService,
                                    RecommendationHistoryService historyService, UserMemoryService memoryService) {
        this.mutationCommitService = mutationCommitService;
        this.sessionStateService = sessionStateService;
        this.sessionService = sessionService;
        this.historyService = historyService;
        this.memoryService = memoryService;
    }

    @Transactional
    public ChatResponse commit(Long userId, String userInput, String traceId, SessionState initialState,
                               AgentRecommendationResult result, List<MealResponse> displayMeals,
                               List<AgentMutation> mutations) {
        mutationCommitService.commit(userId, mutations);
        Intent intent = "CLARIFY".equalsIgnoreCase(result.responseType())
                ? Intent.CLARIFY_NEEDED : mutations == null || mutations.isEmpty()
                ? Intent.MEAL_RECOMMENDATION : Intent.MEAL_PLAN;
        SessionPhase phase = intent == Intent.CLARIFY_NEEDED ? SessionPhase.CLARIFY : SessionPhase.RECOMMEND;
        SessionState state = initialState.withIntent(intent).withSlots(result.resolvedSlots()).withPhase(phase)
                .appendLastRecommendations(displayMeals.stream().map(MealResponse::id).toList());
        sessionStateService.save(state);
        sessionService.appendMessage(state.sessionId(), "user", userInput, null, traceId);
        sessionService.appendMessage(state.sessionId(), "assistant", result.speechText(), intent.name(), traceId);
        memoryService.rememberExplicitPreferences(userId, state.sessionId(), result.resolvedSlots());
        historyService.record(userId, state.sessionId(), traceId, state.sourceMode(), userInput,
                result.resolvedSlots(), result.speechText(), displayMeals);

        if (intent == Intent.CLARIFY_NEEDED) {
            return ChatResponse.clarify(state.sessionId(), traceId,
                    result.clarifyQuestion() == null || result.clarifyQuestion().isBlank()
                            ? result.speechText() : result.clarifyQuestion(), result.missingSlots());
        }
        return ChatResponse.answer(state.sessionId(), traceId, result.speechText(), displayMeals,
                result.nextAction() == null || result.nextAction().isBlank() ? "WAIT_USER" : result.nextAction());
    }
}
