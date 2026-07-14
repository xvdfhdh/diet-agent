package com.diet.model;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record EvaluationReport(
        LocalDateTime startAt,
        LocalDateTime endAt,
        int totalTraces,
        int labeledTraces,
        Double avgScore,
        Map<String, Double> metricAverages,
        List<TraceEvaluationResult> traceResults
) {
}