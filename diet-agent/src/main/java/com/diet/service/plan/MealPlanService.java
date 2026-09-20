package com.diet.service.plan;

import com.diet.enums.AcquisitionMode;
import com.diet.enums.MealPeriod;
import com.diet.enums.PlanStatus;
import com.diet.exception.DietException;
import com.diet.mapper.MealPlanMapper;
import com.diet.model.*;
import com.diet.service.meal.MealService;
import com.diet.service.memory.UserMemoryService;
import com.diet.util.JsonService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class MealPlanService {
    private final MealPlanMapper mapper;
    private final MealService mealService;
    private final UserMemoryService memoryService;
    private final JsonService jsonService;

    public MealPlanService(MealPlanMapper mapper, MealService mealService,
                           UserMemoryService memoryService, JsonService jsonService) {
        this.mapper = mapper;
        this.mealService = mealService;
        this.memoryService = memoryService;
        this.jsonService = jsonService;
    }

    public LocalDate weekStart(LocalDate date) {
        LocalDate value = date == null ? LocalDate.now() : date;
        return value.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    public List<PlanItemResponse> findWeek(Long userId, LocalDate requestedWeekStart) {
        LocalDate start = weekStart(requestedWeekStart);
        return mapper.findRange(userId, start, start.plusDays(6)).stream()
                .map(row -> toResponse(userId, row)).toList();
    }

    public List<MealPlanRow> findWeekRows(Long userId, LocalDate requestedWeekStart) {
        LocalDate start = weekStart(requestedWeekStart);
        return mapper.findRange(userId, start, start.plusDays(6));
    }

    public PlanItemResponse findOwned(Long userId, Long id) {
        return toResponse(userId, requireOwned(userId, id));
    }

    @Transactional
    public PlanItemResponse add(Long userId, PlanItemRequest request) {
        validateRequest(request);
        MealItem meal = requireMeal(userId, request.mealId());
        MealPlanRow row = buildRow(userId, request.planDate(), request.mealPeriod(), meal,
                resolveMode(request.acquisitionMode(), meal.detail().acquisitionMode()), request.servings());
        mapper.upsert(row);
        return toResponse(userId, mapper.findBySlot(userId, row.getPlanDate(), row.getMealPeriod()));
    }

    @Transactional
    public PlanItemResponse update(Long userId, Long id, PlanItemRequest request) {
        MealPlanRow existing = requireOwned(userId, id);
        LocalDate targetDate = request != null && request.planDate() != null ? request.planDate() : existing.getPlanDate();
        MealPeriod targetPeriod = request != null && request.mealPeriod() != null
                ? request.mealPeriod() : MealPeriod.valueOf(existing.getMealPeriod());
        Long targetMealId = request != null && request.mealId() != null ? request.mealId() : existing.getMealId();
        MealItem meal = requireMeal(userId, targetMealId);
        AcquisitionMode requestedMode = request == null ? null : request.acquisitionMode();
        AcquisitionMode mode = resolveMode(requestedMode,
                requestedMode == null && targetMealId.equals(existing.getMealId())
                        ? AcquisitionMode.valueOf(existing.getAcquisitionMode()) : meal.detail().acquisitionMode());
        Integer servings = request != null && request.servings() != null ? request.servings() : existing.getServings();
        if (servings < 1 || servings > 20) throw new DietException("份数需在 1~20 之间");
        MealPlanRow replacement = buildRow(userId, targetDate, targetPeriod, meal, mode, servings);
        replacement.setStatus(targetMealId.equals(existing.getMealId())
                ? existing.getStatus() : PlanStatus.REPLACED.name());
        mapper.upsert(replacement);
        MealPlanRow saved = mapper.findBySlot(userId, targetDate, targetPeriod.name());
        if (!saved.getId().equals(existing.getId())) mapper.deleteOwned(existing.getId(), userId);
        return toResponse(userId, saved);
    }

    @Transactional
    public void delete(Long userId, Long id) {
        if (mapper.deleteOwned(id, userId) == 0) throw new DietException("计划项不存在或无权访问");
    }

    @Transactional
    public List<PlanItemResponse> generate(Long userId, PlanGenerateRequest request) {
        LocalDate start = weekStart(request == null ? null : request.weekStart());
        List<MealItem> accessible = mealService.findAccessibleMeals(userId);
        Set<Long> excluded = new HashSet<>(memoryService.dislikedMealIds(userId));
        Set<Long> recent = new HashSet<>(mapper.findRecentMealIds(userId, start.minusDays(14)));
        Set<String> constraints = memoryService.behaviorConstraints(userId);
        List<MealItem> candidates = accessible.stream().filter(meal -> !excluded.contains(meal.id()))
                .filter(meal -> matchesConstraints(meal, constraints)).toList();
        if (candidates.isEmpty()) throw new DietException("暂无符合长期偏好约束的可用餐食");
        Map<MealPeriod, Integer> cursor = new EnumMap<>(MealPeriod.class);
        for (int day = 0; day < 7; day++) {
            for (MealPeriod period : List.of(MealPeriod.BREAKFAST, MealPeriod.LUNCH, MealPeriod.DINNER)) {
                List<MealItem> matching = candidates.stream()
                        .filter(meal -> meal.slots().mealTime().contains(period.label()) || meal.slots().mealTime().contains("三餐"))
                        .sorted(Comparator.comparing((MealItem meal) -> recent.contains(meal.id())))
                        .toList();
                if (matching.isEmpty()) matching = candidates;
                int index = cursor.getOrDefault(period, 0);
                MealItem selected = matching.get(index % matching.size());
                cursor.put(period, index + 1);
                AcquisitionMode preferred = request == null ? null : request.preferredMode();
                MealPlanRow row = buildRow(userId, start.plusDays(day), period, selected,
                        resolveMode(preferred, selected.detail().acquisitionMode()), selected.detail().defaultServings());
                mapper.upsert(row);
                recent.add(selected.id());
            }
        }
        return findWeek(userId, start);
    }

    @Transactional
    public PlanItemResponse replace(Long userId, Long id) {
        MealPlanRow existing = requireOwned(userId, id);
        MealPeriod period = MealPeriod.valueOf(existing.getMealPeriod());
        Set<Long> excluded = new HashSet<>(memoryService.dislikedMealIds(userId));
        excluded.add(existing.getMealId());
        Set<Long> recent = new HashSet<>(mapper.findRecentMealIds(userId, existing.getPlanDate().minusDays(14)));
        Set<String> constraints = memoryService.behaviorConstraints(userId);
        MealItem current = fromSnapshot(existing);
        MealItem selected = mealService.findAccessibleMeals(userId).stream()
                .filter(meal -> !excluded.contains(meal.id()))
                .filter(meal -> matchesConstraints(meal, constraints))
                .filter(meal -> meal.slots().mealTime().contains(period.label()) || meal.slots().mealTime().contains("三餐"))
                .sorted(Comparator.comparing((MealItem meal) -> recent.contains(meal.id()))
                        .thenComparing(Comparator.comparingInt((MealItem meal) -> similarity(current, meal)).reversed()))
                .findFirst().orElseThrow(() -> new DietException("没有可替换的相似餐食"));
        MealPlanRow row = buildRow(userId, existing.getPlanDate(), period, selected,
                resolveMode(null, selected.detail().acquisitionMode()), existing.getServings());
        row.setStatus(PlanStatus.REPLACED.name());
        mapper.upsert(row);
        return toResponse(userId, mapper.findBySlot(userId, existing.getPlanDate(), existing.getMealPeriod()));
    }

    @Transactional
    public PlanItemResponse checkIn(Long userId, Long id, MealCheckinRequest request) {
        MealPlanRow plan = requireOwned(userId, id);
        MealCheckinRequest safe = request == null
                ? new MealCheckinRequest(null, null, null, null, null, null, null, null, false) : request;
        validateScore(safe.rating(), "评分");
        validateScore(safe.satiety(), "饱腹感");
        if (safe.actualSpent() != null && safe.actualSpent().signum() < 0) throw new DietException("实际花费不能为负数");
        MealItem plannedMeal = fromSnapshot(plan);
        MealItem actualMeal = safe.actualMealId() == null ? plannedMeal : requireMeal(userId, safe.actualMealId());
        boolean skipped = Boolean.TRUE.equals(safe.skipped());
        MealCheckinRow row = new MealCheckinRow();
        row.setUserId(userId); row.setPlanId(id); row.setActualMealId(skipped ? null : actualMeal.id());
        row.setActualMealName(skipped ? safe.actualMealName() :
                (safe.actualMealName() == null || safe.actualMealName().isBlank() ? actualMeal.name() : safe.actualMealName().trim()));
        row.setRating(safe.rating()); row.setSatiety(safe.satiety()); row.setReasonCode(trim(safe.reasonCode()));
        row.setNote(trim(safe.note())); row.setActualSpent(safe.actualSpent());
        row.setEatenAt(safe.eatenAt() == null ? LocalDateTime.now() : safe.eatenAt());
        mapper.upsertCheckin(row);
        mapper.updateStatus(id, userId, skipped ? PlanStatus.SKIPPED.name() : PlanStatus.COMPLETED.name());
        memoryService.rememberPlanOutcome(userId, plannedMeal, skipped, safe.rating(), safe.reasonCode());
        return toResponse(userId, requireOwned(userId, id));
    }

    public WeeklySummaryResponse weeklySummary(Long userId, LocalDate requestedWeekStart) {
        LocalDate start = weekStart(requestedWeekStart);
        List<PlanItemResponse> items = findWeek(userId, start);
        int completed = (int) items.stream().filter(item -> item.status() == PlanStatus.COMPLETED).count();
        int skipped = (int) items.stream().filter(item -> item.status() == PlanStatus.SKIPPED).count();
        int cook = (int) items.stream().filter(item -> item.acquisitionMode() == AcquisitionMode.COOK).count();
        int eatOut = (int) items.stream().filter(item -> item.acquisitionMode() == AcquisitionMode.EAT_OUT).count();
        BigDecimal estimated = items.stream().map(item -> Optional.ofNullable(item.meal().priceMin()).orElse(BigDecimal.ZERO))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal actual = items.stream().map(PlanItemResponse::checkin).filter(Objects::nonNull)
                .map(MealCheckinResponse::actualSpent).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new WeeklySummaryResponse(start, items.size(), completed, skipped,
                items.isEmpty() ? 0 : BigDecimal.valueOf(completed * 100.0 / items.size()).setScale(1, RoundingMode.HALF_UP).doubleValue(),
                cook, eatOut, top(items, MealResponse::taste), top(items, MealResponse::cuisine),
                top(items, MealResponse::healthGoal), topMeals(items, PlanStatus.COMPLETED), topMeals(items, PlanStatus.SKIPPED),
                estimated, actual);
    }

    private List<String> top(List<PlanItemResponse> items, Function<MealResponse, List<String>> getter) {
        Map<String, Long> counts = items.stream().flatMap(item -> getter.apply(item.meal()).stream())
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
        return counts.entrySet().stream().sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(3).map(Map.Entry::getKey).toList();
    }

    private List<String> topMeals(List<PlanItemResponse> items, PlanStatus status) {
        Map<String, Long> counts = items.stream().filter(item -> item.status() == status)
                .collect(Collectors.groupingBy(item -> item.meal().name(), Collectors.counting()));
        return counts.entrySet().stream().sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(3).map(Map.Entry::getKey).toList();
    }

    private MealPlanRow buildRow(Long userId, LocalDate date, MealPeriod period, MealItem meal,
                                 AcquisitionMode mode, Integer servings) {
        MealPlanRow row = new MealPlanRow();
        row.setUserId(userId); row.setPlanDate(date); row.setMealPeriod(period.name()); row.setMealId(meal.id());
        row.setMealSnapshot(jsonService.toJson(MealResponse.from(meal))); row.setAcquisitionMode(mode.name());
        row.setServings(servings == null ? 1 : servings); row.setStatus(PlanStatus.PLANNED.name());
        return row;
    }

    private PlanItemResponse toResponse(Long userId, MealPlanRow row) {
        MealCheckinRow checkin = mapper.findCheckin(row.getId(), userId);
        return new PlanItemResponse(row.getId(), row.getPlanDate(), MealPeriod.valueOf(row.getMealPeriod()), row.getMealId(),
                jsonService.fromJson(row.getMealSnapshot(), MealResponse.class), AcquisitionMode.valueOf(row.getAcquisitionMode()),
                row.getServings(), PlanStatus.valueOf(row.getStatus()), checkin == null ? null : new MealCheckinResponse(
                checkin.getId(), checkin.getActualMealId(), checkin.getActualMealName(), checkin.getRating(), checkin.getSatiety(),
                checkin.getReasonCode(), checkin.getNote(), checkin.getActualSpent(), checkin.getEatenAt()));
    }

    private MealItem requireMeal(Long userId, Long mealId) {
        MealItem meal = mealService.findAccessibleMeal(userId, mealId);
        if (meal == null) throw new DietException("餐食不存在或无权访问");
        return meal;
    }

    private MealItem fromSnapshot(MealPlanRow row) {
        MealResponse snapshot = jsonService.fromJson(row.getMealSnapshot(), MealResponse.class);
        SlotBundle slots = new SlotBundle(snapshot.mealTime(), snapshot.mood(), snapshot.scene(), snapshot.healthGoal(),
                snapshot.cuisine(), snapshot.taste(), snapshot.convenience());
        MealDetail detail = new MealDetail(snapshot.acquisitionMode(), snapshot.prepMinutes(), snapshot.difficulty(),
                snapshot.priceMin(), snapshot.priceMax(), snapshot.defaultServings(),
                Optional.ofNullable(snapshot.ingredients()).orElse(List.of()),
                Optional.ofNullable(snapshot.steps()).orElse(List.of()), snapshot.dineOutTips(),
                Optional.ofNullable(snapshot.substitutes()).orElse(List.of()), snapshot.nutrition());
        return new MealItem(snapshot.id(), snapshot.sourceType(), null, snapshot.name(), snapshot.imageUrl(), slots, detail,
                snapshot.matchScore());
    }

    private MealPlanRow requireOwned(Long userId, Long id) {
        MealPlanRow row = mapper.findOwned(id, userId);
        if (row == null) throw new DietException("计划项不存在或无权访问");
        return row;
    }

    private void validateRequest(PlanItemRequest request) {
        if (request == null || request.planDate() == null || request.mealPeriod() == null || request.mealId() == null)
            throw new DietException("日期、餐次和餐食不能为空");
        if (request.servings() != null && (request.servings() < 1 || request.servings() > 20))
            throw new DietException("份数需在 1~20 之间");
    }

    private AcquisitionMode resolveMode(AcquisitionMode requested, AcquisitionMode supported) {
        AcquisitionMode base = supported == null ? AcquisitionMode.BOTH : supported;
        if (requested != null && requested != AcquisitionMode.BOTH) {
            if (base != AcquisitionMode.BOTH && base != requested) throw new DietException("餐食不支持所选获取方式");
            return requested;
        }
        return base == AcquisitionMode.BOTH ? AcquisitionMode.COOK : base;
    }

    private boolean matchesConstraints(MealItem meal, Set<String> constraints) {
        MealDetail detail = meal.detail() == null ? MealDetail.defaults() : meal.detail();
        if (constraints.contains("TOO_EXPENSIVE") && detail.priceMin() != null
                && detail.priceMin().compareTo(BigDecimal.valueOf(30)) > 0) return false;
        if (constraints.contains("TOO_SLOW") && detail.prepMinutes() != null && detail.prepMinutes() > 45) return false;
        return !constraints.contains("NOT_FILLING") || detail.nutrition() == null
                || detail.nutrition().calories() == null || detail.nutrition().calories() >= 450;
    }

    private int similarity(MealItem source, MealItem candidate) {
        if (source == null || source.slots() == null || candidate.slots() == null) return 0;
        return overlap(source.slots().taste(), candidate.slots().taste()) * 3
                + overlap(source.slots().cuisine(), candidate.slots().cuisine()) * 2
                + overlap(source.slots().healthGoal(), candidate.slots().healthGoal()) * 2
                + overlap(source.slots().convenience(), candidate.slots().convenience());
    }

    private int overlap(List<String> left, List<String> right) {
        if (left == null || right == null) return 0;
        Set<String> values = new HashSet<>(left);
        values.retainAll(right);
        return values.size();
    }

    private void validateScore(Integer score, String label) {
        if (score != null && (score < 1 || score > 5)) throw new DietException(label + "需在 1~5 之间");
    }

    private String trim(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
