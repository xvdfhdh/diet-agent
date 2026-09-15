package com.diet.controller.memory;

import com.diet.constants.DietConstants;
import com.diet.model.UserMemoryResponse;
import com.diet.service.memory.UserMemoryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/diet/memories")
public class UserMemoryController {
    private final UserMemoryService memoryService;

    public UserMemoryController(UserMemoryService memoryService) {
        this.memoryService = memoryService;
    }

    @GetMapping
    public List<UserMemoryResponse> list(
            @RequestHeader(value = DietConstants.USER_ID, defaultValue = "1") Long userId,
            @RequestParam(defaultValue = "12") Integer limit
    ) {
        return memoryService.visible(userId, limit);
    }
}
