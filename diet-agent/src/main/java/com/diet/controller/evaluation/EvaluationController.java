package com.diet.controller.evaluation;

import com.diet.constants.DietConstants;
import com.diet.model.EvaluationReport;
import com.diet.model.EvaluationRequest;
import com.diet.service.evaluation.EvaluationService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/diet/evaluations")
public class EvaluationController {
    private final EvaluationService evaluationService;

    public EvaluationController(EvaluationService evaluationService) {
        this.evaluationService = evaluationService;
    }

    @PostMapping
    public EvaluationReport evaluate(
            @RequestHeader(value = DietConstants.USER_ID, defaultValue = "1") Long userId,
            @RequestBody EvaluationRequest request
    ) {
        return evaluationService.evaluate(userId, request);
    }
}




