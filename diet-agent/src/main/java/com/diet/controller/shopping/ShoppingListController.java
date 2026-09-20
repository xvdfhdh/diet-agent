package com.diet.controller.shopping;

import com.diet.constants.DietConstants;
import com.diet.model.ShoppingItemRequest;
import com.diet.model.ShoppingItemResponse;
import com.diet.model.ShoppingListResponse;
import com.diet.service.shopping.ShoppingListService;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/diet/shopping-lists")
public class ShoppingListController {
    private final ShoppingListService service;
    public ShoppingListController(ShoppingListService service) { this.service = service; }

    @GetMapping
    public ShoppingListResponse get(@RequestAttribute(DietConstants.AUTH_USER_ID) Long userId,
                                    @RequestParam(required = false) LocalDate weekStart) { return service.get(userId, weekStart); }
    @PostMapping("/sync")
    public ShoppingListResponse sync(@RequestAttribute(DietConstants.AUTH_USER_ID) Long userId,
                                     @RequestParam(required = false) LocalDate weekStart) { return service.sync(userId, weekStart); }
    @PostMapping("/items")
    public ShoppingItemResponse add(@RequestAttribute(DietConstants.AUTH_USER_ID) Long userId,
                                    @RequestParam(required = false) LocalDate weekStart,
                                    @RequestBody ShoppingItemRequest request) { return service.addItem(userId, weekStart, request); }
    @PutMapping("/items/{id}")
    public ShoppingItemResponse update(@RequestAttribute(DietConstants.AUTH_USER_ID) Long userId,
                                       @PathVariable Long id, @RequestBody ShoppingItemRequest request) {
        return service.updateItem(userId, id, request);
    }
    @DeleteMapping("/items/{id}")
    public void delete(@RequestAttribute(DietConstants.AUTH_USER_ID) Long userId, @PathVariable Long id) { service.deleteItem(userId, id); }
}
