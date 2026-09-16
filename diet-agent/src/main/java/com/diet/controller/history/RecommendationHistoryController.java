package com.diet.controller.history;

import com.diet.constants.DietConstants;
import com.diet.model.RecommendationHistoryResponse;
import com.diet.service.history.RecommendationHistoryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/diet/recommendations")
public class RecommendationHistoryController {
    private final RecommendationHistoryService historyService;

    public RecommendationHistoryController(RecommendationHistoryService historyService) {
        this.historyService = historyService;
    }

    @GetMapping("/history")
    public List<RecommendationHistoryResponse> list(
            @RequestAttribute(DietConstants.AUTH_USER_ID) Long userId,
            @RequestParam(defaultValue = "20") Integer limit,
            @RequestParam(defaultValue = "today") String scope
    ) {
        return "all".equalsIgnoreCase(scope) ? historyService.recent(userId, limit) : historyService.today(userId, limit);
    }
}
