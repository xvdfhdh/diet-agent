package com.diet.controller.feedback;

import com.diet.constants.DietConstants;
import com.diet.model.FeedbackRequest;
import com.diet.service.feedback.FeedbackService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/diet/feedback")
public class FeedbackController {
    private final FeedbackService feedbackService;

    public FeedbackController(FeedbackService feedbackService) {
        this.feedbackService = feedbackService;
    }

    @PostMapping
    public void save(
            @RequestAttribute(DietConstants.AUTH_USER_ID) Long userId,
            @RequestBody FeedbackRequest request
    ) {
        feedbackService.save(userId, request);
    }
}




