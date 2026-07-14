package com.diet.controller.session;

import com.diet.constants.DietConstants;
import com.diet.model.CreateSessionResponse;
import com.diet.service.session.SessionService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/diet/sessions")
public class SessionController {
    private final SessionService sessionService;

    public SessionController(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    @PostMapping
    public CreateSessionResponse create(@RequestHeader(value = DietConstants.USER_ID, defaultValue = "1") Long userId) {
        return new CreateSessionResponse(sessionService.createSession(userId));
    }
}