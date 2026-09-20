package com.diet.enums;

public enum MealPeriod {
    BREAKFAST("早餐"), LUNCH("午餐"), DINNER("晚餐"), SNACK("加餐");

    private final String label;

    MealPeriod(String label) { this.label = label; }
    public String label() { return label; }
}
