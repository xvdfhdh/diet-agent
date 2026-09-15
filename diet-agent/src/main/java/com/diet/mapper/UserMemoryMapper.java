package com.diet.mapper;

import com.diet.model.UserMemoryRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface UserMemoryMapper {
    int upsert(
            @Param("userId") Long userId,
            @Param("memoryType") String memoryType,
            @Param("memoryKey") String memoryKey,
            @Param("memoryValue") String memoryValue,
            @Param("strengthDelta") double strengthDelta,
            @Param("source") String source,
            @Param("sessionId") String sessionId
    );

    List<UserMemoryRow> findPositiveSlotMemories(@Param("userId") Long userId, @Param("limit") int limit);

    List<UserMemoryRow> findDislikedMeals(@Param("userId") Long userId, @Param("limit") int limit);

    List<UserMemoryRow> findVisible(@Param("userId") Long userId, @Param("limit") int limit);

    int deleteSlotPreferences(@Param("userId") Long userId);
}
