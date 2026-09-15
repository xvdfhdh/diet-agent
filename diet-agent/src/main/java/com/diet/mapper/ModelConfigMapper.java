package com.diet.mapper;

import com.diet.model.ModelConfigRow;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ModelConfigMapper {
    ModelConfigRow findCurrent();

    int upsert(ModelConfigRow row);
}
