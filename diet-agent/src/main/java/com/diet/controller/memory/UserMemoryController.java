package com.diet.controller.memory;

import com.diet.constants.DietConstants;
import com.diet.model.UserMemoryResponse;
import com.diet.model.UserPreferenceProfile;
import com.diet.model.UserPreferenceRequest;
import com.diet.service.memory.UserMemoryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
            @RequestAttribute(DietConstants.AUTH_USER_ID) Long userId,
            @RequestParam(defaultValue = "12") Integer limit
    ) {
        return memoryService.visible(userId, limit);
    }

    @GetMapping("/preferences")
    public UserPreferenceProfile preferences(
            @RequestAttribute(DietConstants.AUTH_USER_ID) Long userId) {
        return memoryService.preferences(userId);
    }

    @PutMapping("/preferences")
    public UserPreferenceProfile updatePreferences(
            @RequestAttribute(DietConstants.AUTH_USER_ID) Long userId,
            @RequestBody UserPreferenceRequest request) {
        return memoryService.replacePreferences(userId, request);
    }
}
