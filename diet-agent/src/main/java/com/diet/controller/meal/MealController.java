package com.diet.controller.meal;

import com.diet.constants.DietConstants;
import com.diet.config.AdminOnly;
import com.diet.model.MealBulkRequest;
import com.diet.model.MealBulkResponse;
import com.diet.model.MealRequest;
import com.diet.model.MealResponse;
import com.diet.service.meal.MealService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestAttribute;
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
    public List<MealResponse> findPersonal(@RequestAttribute(DietConstants.AUTH_USER_ID) Long userId) {
        return mealService.findPersonalMeals(userId).stream().map(MealResponse::from).toList();
    }

    @PostMapping("/personal")
    public MealResponse createPersonal(@RequestAttribute(DietConstants.AUTH_USER_ID) Long userId, @RequestBody MealRequest request) {
        return MealResponse.from(mealService.createPersonalMeal(userId, request));
    }

    @PutMapping("/personal/{mealId}")
    public MealResponse updatePersonal(@RequestAttribute(DietConstants.AUTH_USER_ID) Long userId, @PathVariable Long mealId, @RequestBody MealRequest request) {
        return MealResponse.from(mealService.updatePersonalMeal(userId, mealId, request));
    }

    @DeleteMapping("/personal/{mealId}")
    public void deletePersonal(@RequestAttribute(DietConstants.AUTH_USER_ID) Long userId, @PathVariable Long mealId) {
        mealService.deletePersonalMeal(userId, mealId);
    }

    @GetMapping("/public")
    public List<MealResponse> findPublic() {
        return mealService.findPublicMeals().stream().map(MealResponse::from).toList();
    }

    @AdminOnly
    @PostMapping("/public")
    public MealResponse createPublic(@RequestBody MealRequest request) {
        return MealResponse.from(mealService.createPublicMeal(request));
    }

    @AdminOnly
    @PutMapping("/public/{mealId}")
    public MealResponse updatePublic(@PathVariable Long mealId, @RequestBody MealRequest request) {
        return MealResponse.from(mealService.updatePublicMeal(mealId, request));
    }

    @AdminOnly
    @DeleteMapping("/public/{mealId}")
    public void deletePublic(@PathVariable Long mealId) {
        mealService.deletePublicMeal(mealId);
    }

    @AdminOnly
    @PostMapping("/public/batch")
    public MealBulkResponse bulkPublic(@RequestBody MealBulkRequest request) {
        return mealService.bulkPublicMeals(request);
    }
}
