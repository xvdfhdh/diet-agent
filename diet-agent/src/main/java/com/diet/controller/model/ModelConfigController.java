package com.diet.controller.model;

import com.diet.model.ModelConfigRequest;
import com.diet.model.ModelConfigResponse;
import com.diet.model.ModelConnectionTestResponse;
import com.diet.service.model.ModelConfigService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/diet/model-config")
public class ModelConfigController {
    private final ModelConfigService modelConfigService;

    public ModelConfigController(ModelConfigService modelConfigService) {
        this.modelConfigService = modelConfigService;
    }

    @GetMapping
    public ModelConfigResponse get() {
        return modelConfigService.view();
    }

    @PutMapping
    public ModelConfigResponse save(@RequestBody ModelConfigRequest request) {
        return modelConfigService.save(request);
    }

    @PostMapping("/test")
    public ModelConnectionTestResponse test(@RequestBody ModelConfigRequest request) {
        return modelConfigService.test(request);
    }
}
