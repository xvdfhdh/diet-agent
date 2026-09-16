package com.diet.controller.favorite;

import com.diet.constants.DietConstants;
import com.diet.model.FavoriteMealRequest;
import com.diet.model.FavoriteMealResponse;
import com.diet.service.favorite.FavoriteMealService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/diet/favorites")
public class FavoriteMealController {
    private final FavoriteMealService service;

    public FavoriteMealController(FavoriteMealService service) {
        this.service = service;
    }

    @GetMapping
    public List<FavoriteMealResponse> list(
            @RequestAttribute(DietConstants.AUTH_USER_ID) Long userId,
            @RequestParam(defaultValue = "50") Integer limit) {
        return service.list(userId, limit);
    }

    @PostMapping("/{mealId}")
    public FavoriteMealResponse add(
            @RequestAttribute(DietConstants.AUTH_USER_ID) Long userId,
            @PathVariable Long mealId,
            @RequestBody(required = false) FavoriteMealRequest request) {
        return service.add(userId, mealId, request == null ? null : request.sessionId());
    }

    @DeleteMapping("/{mealId}")
    public void remove(
            @RequestAttribute(DietConstants.AUTH_USER_ID) Long userId,
            @PathVariable Long mealId) {
        service.remove(userId, mealId);
    }
}
