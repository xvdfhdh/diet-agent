package com.diet.mapper;

import com.diet.model.ShoppingItemRow;
import com.diet.model.ShoppingListRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface ShoppingListMapper {
    int ensureList(@Param("userId") Long userId, @Param("weekStart") LocalDate weekStart);
    ShoppingListRow findList(@Param("userId") Long userId, @Param("weekStart") LocalDate weekStart);
    int markSynced(@Param("id") Long id, @Param("userId") Long userId);
    int deleteGenerated(@Param("listId") Long listId, @Param("userId") Long userId);
    int insertItem(ShoppingItemRow row);
    List<ShoppingItemRow> findItems(@Param("listId") Long listId, @Param("userId") Long userId);
    ShoppingItemRow findOwnedItem(@Param("id") Long id, @Param("userId") Long userId);
    int updateItem(ShoppingItemRow row);
    int deleteItem(@Param("id") Long id, @Param("userId") Long userId);
}
