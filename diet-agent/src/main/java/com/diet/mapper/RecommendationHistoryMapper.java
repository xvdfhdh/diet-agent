package com.diet.mapper;

import com.diet.model.RecommendationHistoryRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface RecommendationHistoryMapper {
    int insert(RecommendationHistoryRow row);

    List<RecommendationHistoryRow> findToday(@Param("userId") Long userId, @Param("limit") int limit);
}
