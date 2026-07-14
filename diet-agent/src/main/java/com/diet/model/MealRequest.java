package com.diet.model;

import java.util.List;

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
    private List<String> mealTime;
    private List<String> mood;
    private List<String> scene;
    private List<String> healthGoal;
    private List<String> cuisine;
    private List<String> taste;
    private List<String> convenience;

    public SlotBundle toSlots() {
        return new SlotBundle(mealTime, mood, scene, healthGoal, cuisine, taste, convenience);
    }
}




