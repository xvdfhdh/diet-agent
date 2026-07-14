package com.diet.service.clarify;

import com.diet.model.SlotBundle;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.List;

/**
 * 澄清规则服务。
 * 是否追问由 Java 规则决定，避免 LLM 随机性影响状态机；ClarifyAgent 只负责生成追问文案。
 */
@Service
public class ClarifyRuleService {

    /** 判断当前槽位是否足够进入推荐（missingSlots 为空即足够）。 */
    public boolean hasEnoughSlots(SlotBundle slots) {
        return missingSlots(slots).isEmpty();
    }

    /**
     * 计算当前缺失的关键槽位列表。
     * 必填：mealTime；healthGoal 在无强口味/场景偏好时也必填。
     */
    public List<String> missingSlots(SlotBundle slots) {
        // slots 为 null 时用空 SlotBundle 代替
        SlotBundle safeSlots = slots == null ? SlotBundle.empty() : slots;
        List<String> missing = new ArrayList<>();
        // mealTime（餐次）为空 → 必须追问
        if (safeSlots.mealTime().isEmpty()) {
            missing.add("mealTime");
        }
        // healthGoal 为空且无强口味/场景/便捷偏好 → 必须追问健康目标
        if (safeSlots.healthGoal().isEmpty() && !hasStrongFoodPreference(safeSlots)) {
            missing.add("healthGoal");
        }
        return missing;
    }

    /** 用户已明确菜系/口味/场景/便捷性时，healthGoal 可缺省。 */
    private boolean hasStrongFoodPreference(SlotBundle slots) {
        return !slots.cuisine().isEmpty()
                || !slots.taste().isEmpty()
                || !slots.scene().isEmpty()
                || !slots.convenience().isEmpty();
    }

    /** LLM 澄清失败或返回空时的模板追问文案，按 missingSlots 内容选择。 */
    public String fallbackQuestion(List<String> missingSlots) {
        if (missingSlots == null || missingSlots.isEmpty()) {
            return "你这顿更想按口味来，还是按清淡、顶饱这类目标来？";
        }
        if (missingSlots.contains("mealTime")) {
            return "这顿主要是早餐、午餐还是晚餐？";
        }
        if (missingSlots.contains("healthGoal")) {
            return "这顿更想清淡点、顶饱点，还是按口味来？";
        }
        return "我再确认一下，你这顿最看重口味、健康目标还是方便快捷？";
    }
}