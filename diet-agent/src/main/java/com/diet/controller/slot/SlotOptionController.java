package com.diet.controller.slot;

import com.diet.service.slot.SlotOptionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/diet/slot-options")
public class SlotOptionController {
    private final SlotOptionService slotOptionService;

    public SlotOptionController(SlotOptionService slotOptionService) {
        this.slotOptionService = slotOptionService;
    }

    @GetMapping
    public Map<String, List<String>> findAll() {
        return slotOptionService.findAllOptions();
    }
}




