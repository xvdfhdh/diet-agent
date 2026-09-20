package com.diet.controller.session;

import com.diet.constants.DietConstants;
import com.diet.model.CreateSessionResponse;
import com.diet.service.session.SessionService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.diet.model.SessionSummaryResponse;
import com.diet.model.SessionMessageResponse;
import java.util.List;

@RestController
@RequestMapping("/api/v1/diet/sessions")
public class SessionController {
    private final SessionService sessionService;

    public SessionController(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    @PostMapping
    public CreateSessionResponse create(@RequestAttribute(DietConstants.AUTH_USER_ID) Long userId) {
        return new CreateSessionResponse(sessionService.createSession(userId));
    }

    @GetMapping
    public List<SessionSummaryResponse> recent(
            @RequestAttribute(DietConstants.AUTH_USER_ID) Long userId,
            @RequestParam(required = false) Integer limit) {
        return sessionService.recentSessions(userId, limit);
    }

    @GetMapping("/{sessionId}/messages")
    public List<SessionMessageResponse> messages(
            @RequestAttribute(DietConstants.AUTH_USER_ID) Long userId,
            @PathVariable String sessionId,
            @RequestParam(required = false) Integer limit) {
        return sessionService.messages(userId, sessionId, limit);
    }
}
