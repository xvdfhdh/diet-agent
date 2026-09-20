package com.diet.controller.plan;

import com.diet.constants.DietConstants;
import com.diet.model.*;
import com.diet.service.plan.MealPlanService;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/diet/plans")
public class MealPlanController {
    private final MealPlanService service;
    public MealPlanController(MealPlanService service) { this.service = service; }

    @GetMapping
    public List<PlanItemResponse> week(@RequestAttribute(DietConstants.AUTH_USER_ID) Long userId,
                                       @RequestParam(required = false) LocalDate weekStart) {
        return service.findWeek(userId, weekStart);
    }
    @PostMapping("/items")
    public PlanItemResponse add(@RequestAttribute(DietConstants.AUTH_USER_ID) Long userId,
                                @RequestBody PlanItemRequest request) { return service.add(userId, request); }
    @PutMapping("/items/{id}")
    public PlanItemResponse update(@RequestAttribute(DietConstants.AUTH_USER_ID) Long userId,
                                   @PathVariable Long id, @RequestBody PlanItemRequest request) { return service.update(userId, id, request); }
    @DeleteMapping("/items/{id}")
    public void delete(@RequestAttribute(DietConstants.AUTH_USER_ID) Long userId, @PathVariable Long id) { service.delete(userId, id); }
    @PostMapping("/generate")
    public List<PlanItemResponse> generate(@RequestAttribute(DietConstants.AUTH_USER_ID) Long userId,
                                           @RequestBody(required = false) PlanGenerateRequest request) { return service.generate(userId, request); }
    @PostMapping("/items/{id}/replace")
    public PlanItemResponse replace(@RequestAttribute(DietConstants.AUTH_USER_ID) Long userId,
                                    @PathVariable Long id) { return service.replace(userId, id); }
    @PostMapping("/items/{id}/check-in")
    public PlanItemResponse checkIn(@RequestAttribute(DietConstants.AUTH_USER_ID) Long userId,
                                    @PathVariable Long id, @RequestBody(required = false) MealCheckinRequest request) {
        return service.checkIn(userId, id, request);
    }
    @GetMapping("/weekly-summary")
    public WeeklySummaryResponse summary(@RequestAttribute(DietConstants.AUTH_USER_ID) Long userId,
                                         @RequestParam(required = false) LocalDate weekStart) {
        return service.weeklySummary(userId, weekStart);
    }
}
