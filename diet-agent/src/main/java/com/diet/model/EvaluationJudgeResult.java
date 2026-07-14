package com.diet.model;

public record EvaluationJudgeResult(
        double explanationQuality,
        double naturalness,
        String reason
) {
}