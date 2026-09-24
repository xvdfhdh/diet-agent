package com.diet.controller.agentic;

import com.diet.constants.DietConstants;
import com.diet.model.AgentActionResponse;
import com.diet.service.agentic.PendingAgentActionService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/diet/agent-actions")
public class AgentActionController {
    private final PendingAgentActionService service;
    public AgentActionController(PendingAgentActionService service) { this.service = service; }

    @PostMapping("/{id}/confirm")
    public AgentActionResponse confirm(@RequestAttribute(DietConstants.AUTH_USER_ID) Long userId,
                                       @PathVariable String id) {
        return service.confirm(userId, id);
    }

    @PostMapping("/{id}/cancel")
    public AgentActionResponse cancel(@RequestAttribute(DietConstants.AUTH_USER_ID) Long userId,
                                      @PathVariable String id) {
        return service.cancel(userId, id);
    }
}
