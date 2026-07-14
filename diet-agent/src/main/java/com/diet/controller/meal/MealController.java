package com.diet.controller.meal;

import com.diet.constants.DietConstants;
import com.diet.model.MealRequest;
import com.diet.model.MealResponse;
import com.diet.service.meal.MealService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/diet/meals")
public class MealController {
    private final MealService mealService;

    public MealController(MealService mealService) {
        this.mealService = mealService;
    }

    @GetMapping("/personal")
    public List<MealResponse> findPersonal(@RequestHeader(value = DietConstants.USER_ID, defaultValue = "1") Long userId) {
        return mealService.findPersonalMeals(userId).stream().map(MealResponse::from).toList();
    }

    @PostMapping("/personal")
    public MealResponse createPersonal(@RequestHeader(value = DietConstants.USER_ID, defaultValue = "1") Long userId, @RequestBody MealRequest request) {
        return MealResponse.from(mealService.createPersonalMeal(userId, request));
    }

    @PutMapping("/personal/{mealId}")
    public MealResponse updatePersonal(@RequestHeader(value = DietConstants.USER_ID, defaultValue = "1") Long userId, @PathVariable Long mealId, @RequestBody MealRequest request) {
        return MealResponse.from(mealService.updatePersonalMeal(userId, mealId, request));
    }

    @DeleteMapping("/personal/{mealId}")
    public void deletePersonal(@RequestHeader(value = DietConstants.USER_ID, defaultValue = "1") Long userId, @PathVariable Long mealId) {
        mealService.deletePersonalMeal(userId, mealId);
    }

    @GetMapping("/public")
    public List<MealResponse> findPublic() {
        return mealService.findPublicMeals().stream().map(MealResponse::from).toList();
    }
}