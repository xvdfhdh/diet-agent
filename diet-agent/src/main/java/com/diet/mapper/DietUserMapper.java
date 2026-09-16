package com.diet.mapper;

import com.diet.model.DietUserRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface DietUserMapper {
    int insert(DietUserRow row);

    DietUserRow findByUsername(@Param("username") String username);

    DietUserRow findById(@Param("id") Long id);

    int countEnabledAdmins();

    int incrementTokenVersion(@Param("id") Long id);
}
