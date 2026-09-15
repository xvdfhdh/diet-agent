package com.diet.service.memory;

import com.diet.mapper.UserMemoryMapper;
import com.diet.model.MealItem;
import com.diet.model.SlotBundle;
import com.diet.model.UserMemoryResponse;
import com.diet.model.UserMemoryRow;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class UserMemoryService {
    private static final String SLOT_PREFERENCE = "SLOT_PREFERENCE";
    private static final String MEAL_PREFERENCE = "MEAL_PREFERENCE";
    private static final Set<String> STABLE_SLOT_KEYS = Set.of("healthGoal", "cuisine", "taste", "convenience");

    private final UserMemoryMapper mapper;

    public UserMemoryService(UserMemoryMapper mapper) {
        this.mapper = mapper;
    }

    /** 只记忆相对稳定的偏好，心情、场景和餐次仍以本轮表达为准。 */
    public void rememberExplicitPreferences(Long userId, String sessionId, SlotBundle explicitSlots) {
        if (explicitSlots == null) {
            return;
        }
        rememberValues(userId, sessionId, "healthGoal", explicitSlots.healthGoal(), 1.0, "CONVERSATION");
        rememberValues(userId, sessionId, "cuisine", explicitSlots.cuisine(), 1.0, "CONVERSATION");
        rememberValues(userId, sessionId, "taste", explicitSlots.taste(), 1.0, "CONVERSATION");
        rememberValues(userId, sessionId, "convenience", explicitSlots.convenience(), 1.0, "CONVERSATION");
    }

    /** 点赞会加强餐食和对应标签；负向反馈只排除具体餐食，避免过度推断整类口味。 */
    public void rememberFeedback(Long userId, String sessionId, MealItem meal, String action) {
        if (meal == null || action == null) {
            return;
        }
        String normalized = action.trim().toUpperCase(Locale.ROOT);
        double delta = switch (normalized) {
            case "LIKE", "FAVORITE", "ACCEPT" -> 2.0;
            case "DISLIKE", "HIDE", "SKIP", "REJECT" -> -3.0;
            default -> 0.0;
        };
        if (delta == 0) {
            return;
        }
        mapper.upsert(userId, MEAL_PREFERENCE, meal.name(), String.valueOf(meal.id()), delta, "FEEDBACK", sessionId);
        if (delta > 0) {
            rememberValues(userId, sessionId, "healthGoal", meal.slots().healthGoal(), 0.5, "FEEDBACK");
            rememberValues(userId, sessionId, "cuisine", meal.slots().cuisine(), 0.5, "FEEDBACK");
            rememberValues(userId, sessionId, "taste", meal.slots().taste(), 0.5, "FEEDBACK");
            rememberValues(userId, sessionId, "convenience", meal.slots().convenience(), 0.5, "FEEDBACK");
        }
    }

    /** 本轮已明确的字段绝不覆盖，仅为空字段补入每类最强的一个长期偏好。 */
    public SlotBundle personalize(Long userId, SlotBundle current) {
        SlotBundle safeCurrent = current == null ? SlotBundle.empty() : current;
        Map<String, List<String>> recalled = recallTopSlots(userId);
        return new SlotBundle(
                safeCurrent.mealTime(),
                safeCurrent.mood(),
                safeCurrent.scene(),
                chooseCurrent(safeCurrent.healthGoal(), recalled.get("healthGoal")),
                chooseCurrent(safeCurrent.cuisine(), recalled.get("cuisine")),
                chooseCurrent(safeCurrent.taste(), recalled.get("taste")),
                chooseCurrent(safeCurrent.convenience(), recalled.get("convenience"))
        );
    }

    public List<Long> dislikedMealIds(Long userId) {
        List<Long> result = new ArrayList<>();
        for (UserMemoryRow row : mapper.findDislikedMeals(userId, 100)) {
            try {
                result.add(Long.valueOf(row.getMemoryValue()));
            } catch (NumberFormatException ignored) {
                // 忽略早期脏数据，不中断推荐。
            }
        }
        return List.copyOf(new LinkedHashSet<>(result));
    }

    public List<UserMemoryResponse> visible(Long userId, Integer limit) {
        int safeLimit = Math.max(1, Math.min(limit == null ? 12 : limit, 50));
        return mapper.findVisible(userId, safeLimit).stream()
                .map(row -> new UserMemoryResponse(
                        row.getId(), row.getMemoryType(), row.getMemoryKey(), row.getMemoryValue(),
                        row.getStrength(), row.getEvidenceCount(), row.getSource(), row.getUpdatedAt()
                ))
                .toList();
    }

    private Map<String, List<String>> recallTopSlots(Long userId) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        for (UserMemoryRow row : mapper.findPositiveSlotMemories(userId, 50)) {
            if (!STABLE_SLOT_KEYS.contains(row.getMemoryKey())) {
                continue;
            }
            result.computeIfAbsent(row.getMemoryKey(), ignored -> new ArrayList<>());
            List<String> values = result.get(row.getMemoryKey());
            if (values.isEmpty()) {
                values.add(row.getMemoryValue());
            }
        }
        return result;
    }

    private List<String> chooseCurrent(List<String> current, List<String> recalled) {
        return current == null || current.isEmpty()
                ? (recalled == null ? List.of() : List.copyOf(recalled))
                : current;
    }

    private void rememberValues(
            Long userId,
            String sessionId,
            String key,
            List<String> values,
            double delta,
            String source
    ) {
        if (values == null) {
            return;
        }
        values.stream().filter(value -> value != null && !value.isBlank()).distinct().forEach(value ->
                mapper.upsert(userId, SLOT_PREFERENCE, key, value.trim(), delta, source, sessionId)
        );
    }
}
