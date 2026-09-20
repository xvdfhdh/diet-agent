package com.diet.mapper;

import com.diet.model.MealCheckinRow;
import com.diet.model.MealPlanRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface MealPlanMapper {
    int upsert(MealPlanRow row);
    MealPlanRow findBySlot(@Param("userId") Long userId, @Param("planDate") LocalDate planDate, @Param("mealPeriod") String mealPeriod);
    MealPlanRow findOwned(@Param("id") Long id, @Param("userId") Long userId);
    List<MealPlanRow> findRange(@Param("userId") Long userId, @Param("start") LocalDate start, @Param("end") LocalDate end);
    List<Long> findRecentMealIds(@Param("userId") Long userId, @Param("since") LocalDate since);
    int deleteOwned(@Param("id") Long id, @Param("userId") Long userId);
    int updateStatus(@Param("id") Long id, @Param("userId") Long userId, @Param("status") String status);
    int upsertCheckin(MealCheckinRow row);
    MealCheckinRow findCheckin(@Param("planId") Long planId, @Param("userId") Long userId);
}
