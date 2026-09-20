package com.diet.model;

import java.util.List;
import java.math.BigDecimal;
import com.diet.enums.AcquisitionMode;

import com.fasterxml.jackson.annotation.JsonAutoDetect;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
@Accessors(fluent = true)
@AllArgsConstructor
@NoArgsConstructor
public class MealRequest {
    private String name;
    private String imageUrl;
    private List<String> mealTime;
    private List<String> mood;
    private List<String> scene;
    private List<String> healthGoal;
    private List<String> cuisine;
    private List<String> taste;
    private List<String> convenience;
    private AcquisitionMode acquisitionMode;
    private Integer prepMinutes;
    private String difficulty;
    private BigDecimal priceMin;
    private BigDecimal priceMax;
    private Integer defaultServings;
    private List<MealIngredient> ingredients;
    private List<String> steps;
    private String dineOutTips;
    private List<String> substitutes;
    private NutritionSummary nutrition;

    public MealRequest(String name, String imageUrl, List<String> mealTime, List<String> mood,
                       List<String> scene, List<String> healthGoal, List<String> cuisine,
                       List<String> taste, List<String> convenience) {
        this.name = name; this.imageUrl = imageUrl; this.mealTime = mealTime; this.mood = mood;
        this.scene = scene; this.healthGoal = healthGoal; this.cuisine = cuisine;
        this.taste = taste; this.convenience = convenience;
    }

    public SlotBundle toSlots() {
        return new SlotBundle(mealTime, mood, scene, healthGoal, cuisine, taste, convenience);
    }

    public MealDetail toDetail() {
        return new MealDetail(acquisitionMode == null ? AcquisitionMode.BOTH : acquisitionMode,
                prepMinutes, difficulty, priceMin, priceMax,
                defaultServings == null ? 1 : defaultServings,
                ingredients == null ? List.of() : ingredients,
                steps == null ? List.of() : steps,
                dineOutTips,
                substitutes == null ? List.of() : substitutes,
                nutrition);
    }
}




