package com.diet.service.meal;

import com.diet.exception.DietException;
import com.diet.model.MealItem;
import com.diet.model.MealSearchRequest;
import com.diet.enums.SourceMode;
import org.springframework.stereotype.Service;
import java.util.List;

/**
 * 餐食检索服务（Orchestrator 推荐流水线第一层）。
 * 按 sourceMode、userId、slots 从 DB 召回候选，不负责最终重排和 excludeMealIds 过滤。
 */
@Service
public class MealSearchService {

    /** 底层餐食服务，封装 MyBatis JSON_OVERLAPS 检索。 */
    private final MealService mealService;

    /** 构造器注入 MealService。 */
    public MealSearchService(MealService mealService) {
        this.mealService = mealService;
    }

    /**
     * 执行数据源隔离检索。
     * 由 Orchestrator#completeRecommendation 调用；excludeMealIds 在 MealRankService 层过滤。
     */
    public List<MealItem> search(MealSearchRequest request) {
        // 请求体或 sourceMode 为空时抛异常
        if (request == null || request.sourceMode() == null) {
            throw new DietException("sourceMode 不能为空");
        }

        // PERSONAL 模式必须提供 userId，否则无法查个人库
        if (request.sourceMode() == SourceMode.PERSONAL && request.userId() == null) {
            throw new DietException("PERSONAL 模式必须提供 userId");
        }

        // MealService.search：MySQL JSON_OVERLAPS
        return mealService.search(request.sourceMode(), request.userId(), request.slots());
    }
}