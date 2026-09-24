package com.diet.service.agentic;

import com.diet.enums.Intent;
import com.diet.enums.AgentTaskType;
import com.diet.enums.SourceStrategy;
import com.diet.enums.SessionPhase;
import com.diet.model.*;
import com.diet.service.history.RecommendationHistoryService;
import com.diet.service.memory.UserMemoryService;
import com.diet.service.session.SessionService;
import com.diet.service.session.SessionStateService;
import com.diet.service.session.SessionContextSummaryService;
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
    private final AgentConfirmationPolicy confirmationPolicy;
    private final PendingAgentActionService pendingActions;
    private final SessionContextSummaryService contextSummaries;

    public AgentResultCommitService(AgentMutationCommitService mutationCommitService,
                                    SessionStateService sessionStateService, SessionService sessionService,
                                    RecommendationHistoryService historyService, UserMemoryService memoryService,
                                    AgentConfirmationPolicy confirmationPolicy,
                                    PendingAgentActionService pendingActions,
                                    SessionContextSummaryService contextSummaries) {
        this.mutationCommitService = mutationCommitService;
        this.sessionStateService = sessionStateService;
        this.sessionService = sessionService;
        this.historyService = historyService;
        this.memoryService = memoryService;
        this.confirmationPolicy = confirmationPolicy;
        this.pendingActions = pendingActions;
        this.contextSummaries = contextSummaries;
    }

    @Transactional
    public ChatResponse commit(Long userId, String userInput, String traceId, SessionState initialState,
                               AgentRecommendationResult result, List<MealResponse> displayMeals,
                               List<AgentMutation> mutations, SourceStrategy sourceStrategy) {
        List<AgentMutation> safeMutations = mutations == null ? List.of() : List.copyOf(mutations);
        AgentActionPreview actionPreview = null;
        if (confirmationPolicy.requiresConfirmation(userId, safeMutations)) {
            AgentTaskType taskType = inferTaskType(safeMutations);
            actionPreview = pendingActions.create(userId, initialState.sessionId(), taskType, safeMutations,
                    confirmationPolicy.summary(safeMutations), confirmationPolicy.describe(safeMutations));
        } else {
            mutationCommitService.commit(userId, safeMutations);
        }
        Intent intent = "CLARIFY".equalsIgnoreCase(result.responseType())
                ? Intent.CLARIFY_NEEDED : safeMutations.isEmpty()
                ? Intent.MEAL_RECOMMENDATION : Intent.MEAL_PLAN;
        SessionPhase phase = intent == Intent.CLARIFY_NEEDED ? SessionPhase.CLARIFY : SessionPhase.RECOMMEND;
        SessionState state = initialState.withIntent(intent).withSlots(result.resolvedSlots()).withPhase(phase)
                .appendLastRecommendations(displayMeals.stream().map(MealResponse::id).toList());
        sessionStateService.save(state);
        sessionService.appendMessage(state.sessionId(), "user", userInput, null, traceId);
        sessionService.appendMessage(state.sessionId(), "assistant", result.speechText(), intent.name(), traceId);
        contextSummaries.schedule(userId, state.sessionId());
        memoryService.rememberExplicitPreferences(userId, state.sessionId(), result.resolvedSlots());
        historyService.record(userId, state.sessionId(), traceId, state.sourceMode(), sourceStrategy, userInput,
                result.resolvedSlots(), result.speechText(), displayMeals);

        if (intent == Intent.CLARIFY_NEEDED) {
            return ChatResponse.clarify(state.sessionId(), traceId,
                    result.clarifyQuestion() == null || result.clarifyQuestion().isBlank()
                            ? result.speechText() : result.clarifyQuestion(), result.missingSlots());
        }
        String nextAction = actionPreview == null
                ? (result.nextAction() == null || result.nextAction().isBlank() ? "WAIT_USER" : result.nextAction())
                : "CONFIRM_ACTION";
        return ChatResponse.answer(state.sessionId(), traceId, result.speechText(), displayMeals, nextAction)
                .withActionPreview(actionPreview);
    }

    private AgentTaskType inferTaskType(List<AgentMutation> mutations) {
        if (mutations.stream().anyMatch(value -> value instanceof AgentMutation.SyncShoppingList
                || value instanceof AgentMutation.AddShoppingItem || value instanceof AgentMutation.UpdateShoppingItem
                || value instanceof AgentMutation.DeleteShoppingItem)) return AgentTaskType.SHOPPING;
        if (mutations.stream().anyMatch(value -> value instanceof AgentMutation.CheckInPlanItem)) return AgentTaskType.CHECKIN;
        if (mutations.stream().anyMatch(value -> value instanceof AgentMutation.SetFavorite)) return AgentTaskType.FAVORITE;
        if (mutations.stream().anyMatch(value -> value instanceof AgentMutation.ReplacePreferences)) return AgentTaskType.PREFERENCE;
        return AgentTaskType.PLAN;
    }
}
