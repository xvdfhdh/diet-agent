package com.diet.controller.trace;

import com.diet.constants.DietConstants;
import com.diet.model.RequestTraceRow;
import com.diet.model.TraceLabelRequest;
import com.diet.service.trace.AgentTraceService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1/diet/debug")
public class AgentTraceController {
    private final AgentTraceService agentTraceService;

    public AgentTraceController(AgentTraceService agentTraceService) {
        this.agentTraceService = agentTraceService;
    }

    @GetMapping("/traces/{traceId}")
    public RequestTraceRow findByTraceId(
            @RequestHeader(value = DietConstants.USER_ID, defaultValue = "1") Long userId,
            @PathVariable String traceId
    ) {
        return agentTraceService.findByTraceId(userId, traceId);
    }

    @GetMapping("/sessions/{sessionId}/traces")
    public List<RequestTraceRow> findBySessionId(
            @RequestHeader(value = DietConstants.USER_ID, defaultValue = "1") Long userId,
            @PathVariable String sessionId,
            @RequestParam(value = "limit", required = false) Integer limit
    ) {
        return agentTraceService.findBySessionId(userId, sessionId, limit);
    }

    @GetMapping("/traces")
    public List<RequestTraceRow> findByTimeRange(
            @RequestHeader(value = DietConstants.USER_ID, defaultValue = "1") Long userId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startAt,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endAt,
            @RequestParam(value = "onlyUnlabeled", required = false) Boolean onlyUnlabeled,
            @RequestParam(value = "limit", required = false) Integer limit
    ) {
        return agentTraceService.findByTimeRange(userId, startAt, endAt, onlyUnlabeled, limit);
    }

    @PutMapping("/traces/{traceId}/label")
    public void updateLabel(
            @RequestHeader(value = DietConstants.USER_ID, defaultValue = "1") Long userId,
            @PathVariable String traceId,
            @RequestBody TraceLabelRequest request
    ) {
        agentTraceService.updateLabel(userId, traceId, request);
    }
}




