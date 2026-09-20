package com.diet.service.shopping;

import com.diet.enums.AcquisitionMode;
import com.diet.exception.DietException;
import com.diet.mapper.ShoppingListMapper;
import com.diet.model.*;
import com.diet.service.plan.MealPlanService;
import com.diet.util.JsonService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;

@Service
public class ShoppingListService {
    private final ShoppingListMapper mapper;
    private final MealPlanService planService;
    private final JsonService jsonService;

    public ShoppingListService(ShoppingListMapper mapper, MealPlanService planService, JsonService jsonService) {
        this.mapper = mapper;
        this.planService = planService;
        this.jsonService = jsonService;
    }

    @Transactional
    public ShoppingListResponse get(Long userId, LocalDate requestedWeekStart) {
        ShoppingListRow list = ensure(userId, requestedWeekStart);
        return response(userId, list);
    }

    @Transactional
    public ShoppingListResponse sync(Long userId, LocalDate requestedWeekStart) {
        ShoppingListRow list = ensure(userId, requestedWeekStart);
        List<ShoppingItemRow> oldItems = mapper.findItems(list.getId(), userId);
        Map<String, Boolean> checked = new HashMap<>();
        oldItems.stream().filter(item -> !Boolean.TRUE.equals(item.getManual()))
                .forEach(item -> checked.put(key(item.getName(), item.getUnit()), Boolean.TRUE.equals(item.getCompleted())));
        Map<String, Aggregate> aggregates = new LinkedHashMap<>();
        for (MealPlanRow plan : planService.findWeekRows(userId, list.getWeekStart())) {
            if (!AcquisitionMode.COOK.name().equals(plan.getAcquisitionMode())) continue;
            MealResponse meal = jsonService.fromJson(plan.getMealSnapshot(), MealResponse.class);
            int defaults = meal.defaultServings() == null || meal.defaultServings() < 1 ? 1 : meal.defaultServings();
            BigDecimal multiplier = BigDecimal.valueOf(plan.getServings())
                    .divide(BigDecimal.valueOf(defaults), 4, RoundingMode.HALF_UP);
            for (MealIngredient ingredient : Optional.ofNullable(meal.ingredients()).orElse(List.of())) {
                if (ingredient == null || ingredient.name() == null || ingredient.name().isBlank()) continue;
                String key = key(ingredient.name(), ingredient.unit());
                Aggregate aggregate = aggregates.computeIfAbsent(key, ignored -> new Aggregate(
                        ingredient.name().trim(), blankDefault(ingredient.category(), "其他"), ingredient.unit(), BigDecimal.ZERO,
                        new LinkedHashSet<>()));
                if (ingredient.quantity() != null) aggregate.quantity = aggregate.quantity.add(ingredient.quantity().multiply(multiplier));
                aggregate.mealIds.add(plan.getMealId());
            }
        }
        mapper.deleteGenerated(list.getId(), userId);
        for (Aggregate aggregate : aggregates.values()) {
            ShoppingItemRow row = new ShoppingItemRow();
            row.setListId(list.getId()); row.setUserId(userId); row.setName(aggregate.name); row.setCategory(aggregate.category);
            row.setQuantity(aggregate.quantity.signum() == 0 ? null : aggregate.quantity.stripTrailingZeros()); row.setUnit(aggregate.unit);
            row.setSourceMealIds(jsonService.toJson(aggregate.mealIds)); row.setManual(false);
            row.setCompleted(checked.getOrDefault(key(aggregate.name, aggregate.unit), false));
            mapper.insertItem(row);
        }
        mapper.markSynced(list.getId(), userId);
        return response(userId, mapper.findList(userId, list.getWeekStart()));
    }

    @Transactional
    public ShoppingItemResponse addItem(Long userId, LocalDate requestedWeekStart, ShoppingItemRequest request) {
        validate(request);
        ShoppingListRow list = ensure(userId, requestedWeekStart);
        ShoppingItemRow row = new ShoppingItemRow();
        row.setListId(list.getId()); row.setUserId(userId); row.setName(request.name().trim());
        row.setCategory(blankDefault(request.category(), "其他")); row.setQuantity(request.quantity());
        row.setUnit(trim(request.unit())); row.setSourceMealIds("[]"); row.setManual(true);
        row.setCompleted(Boolean.TRUE.equals(request.completed()));
        mapper.insertItem(row);
        return itemResponse(row);
    }

    @Transactional
    public ShoppingItemResponse updateItem(Long userId, Long id, ShoppingItemRequest request) {
        ShoppingItemRow existing = mapper.findOwnedItem(id, userId);
        if (existing == null) throw new DietException("购物项不存在或无权访问");
        String name = request == null || request.name() == null ? existing.getName() : request.name();
        if (name.isBlank()) throw new DietException("食材名称不能为空");
        existing.setName(name.trim());
        if (request.category() != null) existing.setCategory(blankDefault(request.category(), "其他"));
        if (request.quantity() != null) existing.setQuantity(request.quantity());
        if (request.unit() != null) existing.setUnit(trim(request.unit()));
        if (request.completed() != null) existing.setCompleted(request.completed());
        mapper.updateItem(existing);
        return itemResponse(mapper.findOwnedItem(id, userId));
    }

    @Transactional
    public void deleteItem(Long userId, Long id) {
        if (mapper.deleteItem(id, userId) == 0) throw new DietException("购物项不存在或无权访问");
    }

    private ShoppingListRow ensure(Long userId, LocalDate requestedWeekStart) {
        LocalDate start = planService.weekStart(requestedWeekStart);
        mapper.ensureList(userId, start);
        return mapper.findList(userId, start);
    }

    private ShoppingListResponse response(Long userId, ShoppingListRow list) {
        List<MealPlanRow> plans = planService.findWeekRows(userId, list.getWeekStart());
        boolean needsSync = list.getSyncedAt() == null || plans.stream().anyMatch(plan ->
                plan.getUpdatedAt() != null && plan.getUpdatedAt().isAfter(list.getSyncedAt()));
        return new ShoppingListResponse(list.getId(), list.getWeekStart(), list.getSyncVersion(), list.getStatus(),
                list.getSyncedAt(), needsSync, mapper.findItems(list.getId(), userId).stream().map(this::itemResponse).toList());
    }

    private ShoppingItemResponse itemResponse(ShoppingItemRow row) {
        return new ShoppingItemResponse(row.getId(), row.getName(), row.getCategory(), row.getQuantity(), row.getUnit(),
                jsonService.fromJsonList(row.getSourceMealIds(), Long.class), Boolean.TRUE.equals(row.getManual()),
                Boolean.TRUE.equals(row.getCompleted()));
    }

    private void validate(ShoppingItemRequest request) {
        if (request == null || request.name() == null || request.name().isBlank()) throw new DietException("食材名称不能为空");
        if (request.quantity() != null && request.quantity().signum() < 0) throw new DietException("数量不能为负数");
    }

    private String key(String name, String unit) {
        return name.trim().toLowerCase(Locale.ROOT) + "\u0000" + Optional.ofNullable(unit).orElse("").trim().toLowerCase(Locale.ROOT);
    }

    private String blankDefault(String value, String fallback) { return value == null || value.isBlank() ? fallback : value.trim(); }
    private String trim(String value) { return value == null || value.isBlank() ? null : value.trim(); }

    private static final class Aggregate {
        private final String name;
        private final String category;
        private final String unit;
        private BigDecimal quantity;
        private final Set<Long> mealIds;
        private Aggregate(String name, String category, String unit, BigDecimal quantity, Set<Long> mealIds) {
            this.name = name; this.category = category; this.unit = unit; this.quantity = quantity; this.mealIds = mealIds;
        }
    }
}
