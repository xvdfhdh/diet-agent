package com.diet.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SlotOptionMapper {
    List<String> findEnabledValues(@Param("slotName") String slotName);

    List<String> findSlotNames();
}




