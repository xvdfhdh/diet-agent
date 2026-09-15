package com.diet.controller.history;

import com.diet.constants.DietConstants;
import com.diet.model.RecommendationHistoryResponse;
import com.diet.service.history.RecommendationHistoryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
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
    public List<RecommendationHistoryResponse> today(
            @RequestHeader(value = DietConstants.USER_ID, defaultValue = "1") Long userId,
            @RequestParam(defaultValue = "20") Integer limit
    ) {
        return historyService.today(userId, limit);
    }
}
