package com.diet.service.slot;

import com.diet.exception.DietException;
import com.diet.mapper.SlotOptionMapper;
import com.diet.model.SlotBundle;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class SlotOptionService {
    public static final List<String> SLOT_NAMES = List.of(
            "mealTime", "mood", "scene", "healthGoal", "cuisine", "taste", "convenience"
    );

    private final SlotOptionMapper slotOptionMapper;

    public SlotOptionService(SlotOptionMapper slotOptionMapper) {
        this.slotOptionMapper = slotOptionMapper;
    }

    public Map<String, List<String>> findAllOptions() {
        Map<String, List<String>> result = new LinkedHashMap<>();
        for (String slotName : SLOT_NAMES) {
            result.put(slotName, slotOptionMapper.findEnabledValues(slotName));
        }
        return result;
    }

    public void validate(SlotBundle slots) {
        Map<String, List<String>> options = findAllOptions();
        validateSlot("mealTime", slots.mealTime(), options);
        validateSlot("mood", slots.mood(), options);
        validateSlot("scene", slots.scene(), options);
        validateSlot("healthGoal", slots.healthGoal(), options);
        validateSlot("cuisine", slots.cuisine(), options);
        validateSlot("taste", slots.taste(), options);
        validateSlot("convenience", slots.convenience(), options);
    }

    private void validateSlot(String slotName, List<String> values, Map<String, List<String>> options) {
        if (values == null || values.isEmpty()) {
            return;
        }
        Set<String> allowed = Set.copyOf(options.getOrDefault(slotName, List.of()));
        for (String value : values) {
            if (!allowed.contains(value)) {
                throw new DietException("非法槽位标签: " + slotName + "=" + value);
            }
        }
    }
}