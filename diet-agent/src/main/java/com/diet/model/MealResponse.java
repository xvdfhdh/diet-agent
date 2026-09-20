package com.diet.model;

import java.util.List;

import com.diet.enums.SourceMode;
import com.fasterxml.jackson.annotation.JsonAutoDetect;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import com.diet.enums.AcquisitionMode;
import java.math.BigDecimal;

@Data
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
@Accessors(fluent = true)
@AllArgsConstructor
@NoArgsConstructor
public class MealResponse {
    private Long id;
    private SourceMode sourceType;
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
    private double matchScore;

    public MealResponse(Long id, SourceMode sourceType, String name, String imageUrl,
                        List<String> mealTime, List<String> mood, List<String> scene,
                        List<String> healthGoal, List<String> cuisine, List<String> taste,
                        List<String> convenience, double matchScore) {
        this(id, sourceType, name, imageUrl, mealTime, mood, scene, healthGoal, cuisine, taste,
                convenience, AcquisitionMode.BOTH, null, null, null, null, 1,
                List.of(), List.of(), null, List.of(), null, matchScore);
    }

    public static MealResponse from(MealItem item) {
        SlotBundle slots = item.slots();
        MealDetail detail = item.detail() == null ? MealDetail.defaults() : item.detail();
        return new MealResponse(
                item.id(),
                item.sourceType(),
                item.name(),
                item.imageUrl(),
                slots.mealTime(),
                slots.mood(),
                slots.scene(),
                slots.healthGoal(),
                slots.cuisine(),
                slots.taste(),
                slots.convenience(),
                detail.acquisitionMode(), detail.prepMinutes(), detail.difficulty(),
                detail.priceMin(), detail.priceMax(), detail.defaultServings(),
                detail.ingredients(), detail.steps(), detail.dineOutTips(),
                detail.substitutes(), detail.nutrition(),
                item.matchScore()
        );
    }
}




