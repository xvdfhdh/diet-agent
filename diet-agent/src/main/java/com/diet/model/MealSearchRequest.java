package com.diet.model;

import java.util.List;

import com.diet.enums.SourceMode;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 餐食检索请求。
 * 将 sourceMode、userId、slots 和 excludeMealIds 显式打包，避免个人库和公共库混查。
 */
@Data
@Accessors(fluent = true)
@AllArgsConstructor
public class MealSearchRequest {
    /** 本轮明确选择的数据源模式，不能为空。 */
    private SourceMode sourceMode;
    /** 当前用户 ID，PERSONAL 模式必须使用。 */
    private Long userId;
    /** 用于 MySQL JSON_OVERLAPS 的标准槽位。 */
    private SlotBundle slots;
    /** 需要从结果中排除的上一轮推荐餐食。 */
    private List<Long> excludeMealIds;
}