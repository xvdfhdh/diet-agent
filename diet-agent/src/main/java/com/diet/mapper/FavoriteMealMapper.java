package com.diet.mapper;

import com.diet.model.FavoriteMealRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface FavoriteMealMapper {
    int upsert(FavoriteMealRow row);

    List<FavoriteMealRow> findByUserId(@Param("userId") Long userId, @Param("limit") int limit);

    int delete(@Param("userId") Long userId, @Param("mealId") Long mealId);
}
