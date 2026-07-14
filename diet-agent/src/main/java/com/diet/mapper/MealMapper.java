package com.diet.mapper;

import com.diet.model.MealItemRow;
import com.diet.enums.SourceMode;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface MealMapper {
    int insert(MealItemRow row);

    int updatePersonal(MealItemRow row);

    int deletePersonal(@Param("id") Long id, @Param("userId") Long userId);

    MealItemRow findPersonalById(@Param("id") Long id, @Param("userId") Long userId);

    List<MealItemRow> findPersonalMeals(Long userId);

    List<MealItemRow> findPublicMeals();

    int countPersonalMeals(Long userId);

    List<MealItemRow> search(
            @Param("sourceMode") SourceMode sourceMode,
            @Param("userId") Long userId,
            @Param("mealTimeJson") String mealTimeJson,
            @Param("moodJson") String moodJson,
            @Param("sceneJson") String sceneJson,
            @Param("healthGoalJson") String healthGoalJson,
            @Param("cuisineJson") String cuisineJson,
            @Param("tasteJson") String tasteJson,
            @Param("convenienceJson") String convenienceJson,
            @Param("limit") int limit
    );
}




