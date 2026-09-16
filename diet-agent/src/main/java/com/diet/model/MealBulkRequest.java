package com.diet.model;

import java.util.List;

public record MealBulkRequest(List<MealRequest> creates, List<MealBulkUpdate> updates, List<Long> deleteIds) {
}
