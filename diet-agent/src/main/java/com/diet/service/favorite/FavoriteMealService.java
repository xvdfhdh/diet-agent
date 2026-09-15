package com.diet.service.favorite;

import com.diet.exception.DietException;
import com.diet.mapper.FavoriteMealMapper;
import com.diet.model.FavoriteMealResponse;
import com.diet.model.FavoriteMealRow;
import com.diet.model.MealItem;
import com.diet.model.MealResponse;
import com.diet.service.meal.MealService;
import com.diet.service.memory.UserMemoryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.time.LocalDateTime;

@Service
public class FavoriteMealService {
    private final FavoriteMealMapper mapper;
    private final MealService mealService;
    private final UserMemoryService memoryService;
    private final ObjectMapper objectMapper;

    public FavoriteMealService(FavoriteMealMapper mapper, MealService mealService,
                               UserMemoryService memoryService, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.mealService = mealService;
        this.memoryService = memoryService;
        this.objectMapper = objectMapper;
    }

    public List<FavoriteMealResponse> list(Long userId, Integer limit) {
        int safeLimit = Math.max(1, Math.min(limit == null ? 50 : limit, 100));
        return mapper.findByUserId(userId, safeLimit).stream().map(this::toResponse).toList();
    }

    @Transactional
    public FavoriteMealResponse add(Long userId, Long mealId, String sessionId) {
        MealItem meal = mealService.findAccessibleMeal(userId, mealId);
        if (meal == null) {
            throw new DietException("餐食不存在或无权访问");
        }
        FavoriteMealRow row = new FavoriteMealRow();
        row.setUserId(userId);
        row.setMealId(mealId);
        row.setSessionId(sessionId);
        row.setMealJson(writeJson(MealResponse.from(meal)));
        row.setCreatedAt(LocalDateTime.now());
        mapper.upsert(row);
        memoryService.rememberFeedback(userId, sessionId, meal, "FAVORITE");
        return new FavoriteMealResponse(MealResponse.from(meal), row.getCreatedAt());
    }

    @Transactional
    public void remove(Long userId, Long mealId) {
        mapper.delete(userId, mealId);
    }

    private FavoriteMealResponse toResponse(FavoriteMealRow row) {
        try {
            return new FavoriteMealResponse(objectMapper.readValue(row.getMealJson(), MealResponse.class), row.getCreatedAt());
        } catch (Exception error) {
            throw new DietException("收藏餐食解析失败", error);
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception error) {
            throw new DietException("收藏餐食序列化失败", error);
        }
    }
}
