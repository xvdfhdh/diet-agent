package com.diet.mapper;

import com.diet.model.PendingAgentActionRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface PendingAgentActionMapper {
    int insert(PendingAgentActionRow row);
    PendingAgentActionRow findOwned(@Param("id") String id, @Param("userId") Long userId);
    PendingAgentActionRow findLatestPending(@Param("sessionId") String sessionId, @Param("userId") Long userId);
    int updateStatus(@Param("id") String id, @Param("userId") Long userId,
                     @Param("expectedStatus") String expectedStatus, @Param("status") String status);
    int expireOwned(@Param("id") String id, @Param("userId") Long userId);
}
