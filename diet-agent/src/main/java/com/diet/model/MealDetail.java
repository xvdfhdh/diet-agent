package com.diet.model;

import com.diet.enums.AcquisitionMode;
import java.math.BigDecimal;
import java.util.List;

public record MealDetail(
        AcquisitionMode acquisitionMode,
        Integer prepMinutes,
        String difficulty,
        BigDecimal priceMin,
        BigDecimal priceMax,
        Integer defaultServings,
        List<MealIngredient> ingredients,
        List<String> steps,
        String dineOutTips,
        List<String> substitutes,
        NutritionSummary nutrition
) {
    public static MealDetail defaults() {
        return new MealDetail(AcquisitionMode.BOTH, null, null, null, null, 1,
                List.of(), List.of(), null, List.of(), null);
    }
}
