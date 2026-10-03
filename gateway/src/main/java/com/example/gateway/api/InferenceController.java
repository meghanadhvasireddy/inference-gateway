package com.example.gateway.api;

import com.example.gateway.service.InferenceService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/inference")
public class InferenceController {
    private final InferenceService service;
    public InferenceController(InferenceService service) { this.service = service; }
    @PostMapping
    public InferenceResponseDto infer(@Valid @RequestBody InferenceRequestDto request) { return service.infer(request); }
}
