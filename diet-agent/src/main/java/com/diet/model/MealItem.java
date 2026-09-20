package com.diet.model;

import com.diet.enums.SourceMode;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(fluent = true)
@AllArgsConstructor
public class MealItem {
    private Long id;
    private SourceMode sourceType;
    private Long ownerUserId;
    private String name;
    private String imageUrl;
    private SlotBundle slots;
    private MealDetail detail;
    private double matchScore;

    public MealItem(Long id, SourceMode sourceType, Long ownerUserId, String name, String imageUrl,
                    SlotBundle slots, double matchScore) {
        this(id, sourceType, ownerUserId, name, imageUrl, slots, MealDetail.defaults(), matchScore);
    }

    public double matchScore() {
        return matchScore;
    }
}




