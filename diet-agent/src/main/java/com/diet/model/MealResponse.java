package com.diet.model;

import java.util.List;

import com.diet.enums.SourceMode;
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
public class MealResponse {
    private Long id;
    private SourceMode sourceType;
    private String name;
    private List<String> mealTime;
    private List<String> mood;
    private List<String> scene;
    private List<String> healthGoal;
    private List<String> cuisine;
    private List<String> taste;
    private List<String> convenience;
    private double matchScore;

    public static MealResponse from(MealItem item) {
        SlotBundle slots = item.slots();
        return new MealResponse(
                item.id(),
                item.sourceType(),
                item.name(),
                slots.mealTime(),
                slots.mood(),
                slots.scene(),
                slots.healthGoal(),
                slots.cuisine(),
                slots.taste(),
                slots.convenience(),
                item.matchScore()
        );
    }
}




