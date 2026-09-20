package com.diet.service.meal;

import com.diet.enums.AcquisitionMode;
import com.diet.exception.DietException;
import com.diet.model.*;
import com.diet.service.memory.UserMemoryService;
import com.diet.service.model.ModelConfigService;
import com.diet.service.slot.SlotOptionService;
import com.diet.util.JsonService;
import com.diet.util.LlmJsonService;
import com.fasterxml.jackson.databind.JsonNode;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.memory.InMemoryMemory;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class MealAiService {
    private static final Pattern NUMBER = Pattern.compile("-?\\d+(?:\\.\\d+)?");
    private static final String SYSTEM_PROMPT = """
            你是日常餐食资料助手。只返回合法 JSON，不要 Markdown、解释或额外文字。
            营养与价格只能给出保守估算，不能声称医疗效果。
            食材 category 优先使用：蔬菜、肉蛋奶、主食、调味品、水果、其他。
            acquisitionMode 只能是 COOK、EAT_OUT、BOTH；difficulty 使用 简单、适中、进阶。
            """;

    private final ModelConfigService modelConfigService;
    private final SlotOptionService slotOptionService;
    private final UserMemoryService memoryService;
    private final MealService mealService;
    private final LlmJsonService llmJsonService;
    private final JsonService jsonService;

    public MealAiService(ModelConfigService modelConfigService, SlotOptionService slotOptionService,
                         UserMemoryService memoryService, MealService mealService,
                         LlmJsonService llmJsonService, JsonService jsonService) {
        this.modelConfigService = modelConfigService;
        this.slotOptionService = slotOptionService;
        this.memoryService = memoryService;
        this.mealService = mealService;
        this.llmJsonService = llmJsonService;
        this.jsonService = jsonService;
    }

    public MealRequest complete(MealRequest current) {
        if (current == null || current.name() == null || current.name().isBlank()) {
            throw new DietException("请先填写餐食名称");
        }
        Map<String, List<String>> options = slotOptionService.findAllOptions();
        String prompt = """
                请根据餐食名称和用户已经填写的信息，补全尚未填写的字段。
                已填写内容必须原样保留，不要改写。图片地址不要编造。
                合法标签字典：%s
                当前餐食：%s
                返回一个餐食 JSON 对象，字段：name,imageUrl,mealTime,mood,scene,healthGoal,cuisine,taste,convenience,
                acquisitionMode,prepMinutes,difficulty,priceMin,priceMax,defaultServings,ingredients,steps,
                dineOutTips,substitutes,nutrition。ingredients 每项包含 name,category,quantity,unit；
                nutrition 包含 calories,protein,fat,carbs。
                """.formatted(options, jsonService.toJson(current));
        MealRequest generated = parseMeal(call(prompt));
        return sanitize(merge(current, generated), options, current.name().trim());
    }

    @Transactional
    public List<MealItem> expandPersonal(Long userId, MealAiExpandRequest request) {
        int count = Math.max(1, Math.min(request == null || request.count() == null ? 3 : request.count(), 8));
        String freePreference = request == null || request.preference() == null ? "" : request.preference().trim();
        UserPreferenceProfile profile = memoryService.preferences(userId);
        Map<String, List<String>> options = slotOptionService.findAllOptions();
        List<String> existingNames = mealService.findPersonalMeals(userId).stream().map(MealItem::name).toList();
        String prompt = """
                为用户生成 %d 道适合加入“我的餐食”的不同餐食。
                长期偏好：%s
                用户本次自由偏好：%s
                已有个人餐食名称（不要重复）：%s
                合法标签字典：%s
                返回 JSON 对象 {"meals":[...]}。每个 meal 字段与以下结构一致：
                name,imageUrl(null),mealTime,mood,scene,healthGoal,cuisine,taste,convenience,
                acquisitionMode,prepMinutes,difficulty,priceMin,priceMax,defaultServings,ingredients,steps,
                dineOutTips,substitutes,nutrition。食材和步骤应具体、适合日常执行。
                """.formatted(count, profile, freePreference.isBlank() ? "无额外要求" : freePreference,
                existingNames, options);
        JsonNode root = llmJsonService.parseValue(call(prompt));
        JsonNode mealsNode = mealArray(root);
        if (!mealsNode.isArray() || mealsNode.isEmpty()) throw new DietException("AI 没有生成有效餐食，请调整偏好后重试");
        LinkedHashSet<String> usedNames = existingNames.stream().map(this::nameKey)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        List<MealRequest> drafts = new ArrayList<>();
        for (JsonNode node : mealsNode) {
            if (drafts.size() >= count) break;
            MealRequest parsed = sanitize(parseMealNode(node), options, null);
            if (parsed.name() != null && usedNames.add(nameKey(parsed.name()))) drafts.add(parsed);
        }
        if (drafts.isEmpty()) throw new DietException("AI 生成的餐食与个人库重复，请换一种偏好重试");
        List<MealItem> created = new ArrayList<>();
        for (MealRequest draft : drafts) created.add(mealService.createPersonalMeal(userId, draft));
        return List.copyOf(created);
    }

    protected String call(String prompt) {
        try {
            ReActAgent agent = ReActAgent.builder().name("diet_meal_editor")
                    .model(modelConfigService.mainModel()).sysPrompt(SYSTEM_PROMPT)
                    .memory(new InMemoryMemory()).build();
            Msg response = agent.call(Msg.builder().role(MsgRole.USER).textContent(prompt).build()).block();
            if (response == null || response.getTextContent() == null || response.getTextContent().isBlank()) {
                throw new DietException("AI 未返回内容");
            }
            return response.getTextContent();
        } catch (DietException error) {
            throw error;
        } catch (Exception error) {
            String message = error.getMessage() == null ? "模型调用失败" : error.getMessage();
            throw new DietException("AI 生成失败，请检查模型配置：" + abbreviate(message, 180), error);
        }
    }

    private MealRequest parseMeal(String content) {
        JsonNode node = llmJsonService.parseValue(content);
        if (node.isObject() && node.path("meal").isObject()) node = node.path("meal");
        return parseMealNode(node);
    }

    private JsonNode mealArray(JsonNode root) {
        if (root.isArray()) return root;
        JsonNode node = root.path("meals");
        if (!node.isArray() && root.path("data").isArray()) node = root.path("data");
        if (!node.isArray() && root.path("data").path("meals").isArray()) node = root.path("data").path("meals");
        if (node.isTextual()) {
            try { node = llmJsonService.parseValue(node.asText()); }
            catch (DietException ignored) { return node; }
        }
        return node;
    }

    /** 按字段容错读取，避免“20元”“适量”或未知枚举让整个批次 JSON 绑定失败。 */
    private MealRequest parseMealNode(JsonNode node) {
        if (node == null || !node.isObject()) throw new DietException("AI 返回的餐食结构不是 JSON 对象");
        List<MealIngredient> ingredients = new ArrayList<>();
        if (node.path("ingredients").isArray()) {
            for (JsonNode item : node.path("ingredients")) {
                if (item.isTextual()) ingredients.add(new MealIngredient(item.asText(), "其他", null, null));
                else if (item.isObject()) ingredients.add(new MealIngredient(text(item, "name"), text(item, "category"),
                        decimal(item.path("quantity")), text(item, "unit")));
            }
        }
        JsonNode nutritionNode = node.path("nutrition");
        NutritionSummary nutrition = nutritionNode.isObject() ? new NutritionSummary(integer(nutritionNode.path("calories")),
                decimal(nutritionNode.path("protein")), decimal(nutritionNode.path("fat")),
                decimal(nutritionNode.path("carbs"))) : null;
        return new MealRequest(text(node, "name"), text(node, "imageUrl"), strings(node.path("mealTime")),
                strings(node.path("mood")), strings(node.path("scene")), strings(node.path("healthGoal")),
                strings(node.path("cuisine")), strings(node.path("taste")), strings(node.path("convenience")),
                acquisitionMode(node.path("acquisitionMode")), integer(node.path("prepMinutes")),
                text(node, "difficulty"), decimal(node.path("priceMin")), decimal(node.path("priceMax")),
                integer(node.path("defaultServings")), ingredients, strings(node.path("steps")),
                text(node, "dineOutTips"), strings(node.path("substitutes")), nutrition);
    }

    private List<String> strings(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) return List.of();
        if (node.isTextual()) return node.asText().isBlank() ? List.of() : List.of(node.asText().trim());
        if (!node.isArray()) return List.of();
        List<String> values = new ArrayList<>();
        for (JsonNode item : node) if (item.isValueNode() && !item.asText().isBlank()) values.add(item.asText().trim());
        return values;
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asText(null);
    }

    private BigDecimal decimal(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) return null;
        if (node.isNumber()) return node.decimalValue();
        Matcher matcher = NUMBER.matcher(node.asText(""));
        try { return matcher.find() ? new BigDecimal(matcher.group()) : null; }
        catch (NumberFormatException ignored) { return null; }
    }

    private Integer integer(JsonNode node) {
        BigDecimal value = decimal(node);
        return value == null ? null : value.intValue();
    }

    private AcquisitionMode acquisitionMode(JsonNode node) {
        String value = node == null ? "" : node.asText("").trim().toUpperCase(Locale.ROOT);
        if (value.contains("COOK") || value.contains("做饭") || value.contains("烹饪")) return AcquisitionMode.COOK;
        if (value.contains("EAT_OUT") || value.contains("外食") || value.contains("外卖")) return AcquisitionMode.EAT_OUT;
        if (value.contains("BOTH") || value.contains("均可") || value.contains("都可")) return AcquisitionMode.BOTH;
        return null;
    }

    private MealRequest merge(MealRequest current, MealRequest generated) {
        NutritionSummary currentNutrition = current.nutrition();
        NutritionSummary generatedNutrition = generated.nutrition();
        NutritionSummary nutrition = currentNutrition == null ? generatedNutrition : new NutritionSummary(
                choose(currentNutrition.calories(), generatedNutrition == null ? null : generatedNutrition.calories()),
                choose(currentNutrition.protein(), generatedNutrition == null ? null : generatedNutrition.protein()),
                choose(currentNutrition.fat(), generatedNutrition == null ? null : generatedNutrition.fat()),
                choose(currentNutrition.carbs(), generatedNutrition == null ? null : generatedNutrition.carbs()));
        return new MealRequest(current.name(), current.imageUrl(),
                chooseList(current.mealTime(), generated.mealTime()), chooseList(current.mood(), generated.mood()),
                chooseList(current.scene(), generated.scene()), chooseList(current.healthGoal(), generated.healthGoal()),
                chooseList(current.cuisine(), generated.cuisine()), chooseList(current.taste(), generated.taste()),
                chooseList(current.convenience(), generated.convenience()),
                current.acquisitionMode() == null ? generated.acquisitionMode() : current.acquisitionMode(),
                choose(current.prepMinutes(), generated.prepMinutes()), chooseText(current.difficulty(), generated.difficulty()),
                choose(current.priceMin(), generated.priceMin()), choose(current.priceMax(), generated.priceMax()),
                choose(current.defaultServings(), generated.defaultServings()),
                chooseList(current.ingredients(), generated.ingredients()), chooseList(current.steps(), generated.steps()),
                chooseText(current.dineOutTips(), generated.dineOutTips()),
                chooseList(current.substitutes(), generated.substitutes()), nutrition);
    }

    private MealRequest sanitize(MealRequest raw, Map<String, List<String>> options, String forcedName) {
        String name = forcedName == null ? clean(raw.name()) : forcedName;
        if (name == null) throw new DietException("AI 未生成餐食名称");
        AcquisitionMode mode = raw.acquisitionMode() == null ? AcquisitionMode.BOTH : raw.acquisitionMode();
        List<MealIngredient> ingredients = safe(raw.ingredients()).stream()
                .filter(item -> item != null && clean(item.name()) != null)
                .map(item -> new MealIngredient(clean(item.name()), defaultText(item.category(), "其他"),
                        nonNegative(item.quantity()), clean(item.unit()))).limit(30).toList();
        List<String> mealTime = allowed(raw.mealTime(), options.get("mealTime"));
        if (mealTime.isEmpty()) mealTime = fallbackMealTime(options);
        BigDecimal priceMin = nonNegative(raw.priceMin());
        BigDecimal priceMax = nonNegative(raw.priceMax());
        if (priceMin != null && priceMax != null && priceMin.compareTo(priceMax) > 0) {
            BigDecimal lower = priceMax;
            priceMax = priceMin;
            priceMin = lower;
        }
        NutritionSummary nutrition = sanitizeNutrition(raw.nutrition());
        return new MealRequest(name, clean(raw.imageUrl()), mealTime,
                allowed(raw.mood(), options.get("mood")), allowed(raw.scene(), options.get("scene")),
                allowed(raw.healthGoal(), options.get("healthGoal")), allowed(raw.cuisine(), options.get("cuisine")),
                allowed(raw.taste(), options.get("taste")), allowed(raw.convenience(), options.get("convenience")),
                mode, clamp(raw.prepMinutes(), 0, 1440), allowedDifficulty(raw.difficulty()), priceMin,
                priceMax, clamp(raw.defaultServings(), 1, 20), ingredients,
                safeStrings(raw.steps(), 20), clean(raw.dineOutTips()), safeStrings(raw.substitutes(), 12), nutrition);
    }

    private NutritionSummary sanitizeNutrition(NutritionSummary value) {
        if (value == null) return null;
        Integer calories = value.calories() == null || value.calories() < 0 ? null : value.calories();
        BigDecimal protein = nonNegative(value.protein());
        BigDecimal fat = nonNegative(value.fat());
        BigDecimal carbs = nonNegative(value.carbs());
        return calories == null && protein == null && fat == null && carbs == null
                ? null : new NutritionSummary(calories, protein, fat, carbs);
    }

    private String allowedDifficulty(String value) {
        String cleaned = clean(value);
        return cleaned != null && List.of("简单", "适中", "进阶").contains(cleaned) ? cleaned : null;
    }

    private List<String> fallbackMealTime(Map<String, List<String>> options) {
        List<String> values = options.getOrDefault("mealTime", List.of());
        if (values.contains("午餐")) return List.of("午餐");
        return values.isEmpty() ? List.of() : List.of(values.get(0));
    }

    private List<String> allowed(List<String> values, List<String> legal) {
        if (values == null || legal == null) return List.of();
        return values.stream().filter(legal::contains).distinct().toList();
    }

    private List<String> safeStrings(List<String> values, int limit) {
        if (values == null) return List.of();
        return values.stream().map(this::clean).filter(value -> value != null).distinct().limit(limit).toList();
    }

    private <T> List<T> safe(List<T> values) { return values == null ? List.of() : values; }
    private <T> List<T> chooseList(List<T> current, List<T> generated) { return current == null || current.isEmpty() ? safe(generated) : current; }
    private <T> T choose(T current, T generated) { return current == null ? generated : current; }
    private String chooseText(String current, String generated) { return clean(current) == null ? generated : current; }
    private String clean(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private String defaultText(String value, String fallback) { String cleaned = clean(value); return cleaned == null ? fallback : cleaned; }
    private BigDecimal nonNegative(BigDecimal value) { return value == null || value.signum() < 0 ? null : value; }
    private Integer clamp(Integer value, int min, int max) { return value == null ? null : Math.max(min, Math.min(max, value)); }
    private String abbreviate(String value, int max) { return value.length() <= max ? value : value.substring(0, max) + "…"; }
    private String nameKey(String value) { return value == null ? "" : value.trim().toLowerCase(java.util.Locale.ROOT); }
}
