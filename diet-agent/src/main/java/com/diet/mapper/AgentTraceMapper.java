package com.diet.mapper;

import com.diet.model.RequestTraceRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface AgentTraceMapper {
    int insert(RequestTraceRow row);

    RequestTraceRow findByTraceId(@Param("userId") Long userId, @Param("traceId") String traceId);

    List<RequestTraceRow> findBySessionId(
            @Param("userId") Long userId,
            @Param("sessionId") String sessionId,
            @Param("limit") int limit
    );

    List<RequestTraceRow> findByTimeRange(
            @Param("userId") Long userId,
            @Param("startAt") LocalDateTime startAt,
            @Param("endAt") LocalDateTime endAt,
            @Param("onlyUnlabeled") boolean onlyUnlabeled,
            @Param("limit") int limit
    );

    int updateLabel(
            @Param("userId") Long userId,
            @Param("traceId") String traceId,
            @Param("expectedIntent") String expectedIntent,
            @Param("expectedSlots") String expectedSlots,
            @Param("expectedClarifyAction") String expectedClarifyAction,
            @Param("labeledBy") Long labeledBy,
            @Param("labelNote") String labelNote
    );
}




