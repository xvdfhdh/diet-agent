package com.diet.service.feedback;

import com.diet.exception.DietException;
import com.diet.mapper.FeedbackMapper;
import com.diet.model.FeedbackRequest;
import com.diet.model.MealItem;
import com.diet.service.meal.MealService;
import com.diet.service.memory.UserMemoryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FeedbackService {
    private final FeedbackMapper feedbackMapper;
    private final MealService mealService;
    private final UserMemoryService memoryService;

    public FeedbackService(FeedbackMapper feedbackMapper, MealService mealService, UserMemoryService memoryService) {
        this.feedbackMapper = feedbackMapper;
        this.mealService = mealService;
        this.memoryService = memoryService;
    }

    @Transactional
    public void save(Long userId, FeedbackRequest request) {
        if (request == null || request.sessionId() == null || request.sessionId().isBlank()) {
            throw new DietException("反馈 sessionId 不能为空");
        }
        if (request.action() == null || request.action().isBlank()) {
            throw new DietException("反馈 action 不能为空");
        }
        feedbackMapper.insert(
                userId,
                request.sessionId(),
                request.itemId(),
                request.action(),
                request.rating(),
                request.reason()
        );
        MealItem meal = mealService.findAccessibleMeal(userId, request.itemId());
        memoryService.rememberFeedback(userId, request.sessionId(), meal, request.action());
    }
}
