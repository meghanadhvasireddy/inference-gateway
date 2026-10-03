package com.example.gateway.api;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record InferenceRequestDto(
        @NotBlank String model,
        @NotBlank @Size(max = 20000) String prompt,
        @DecimalMin("0.0") @DecimalMax("2.0") double temperature,
        @Min(1) @Max(4096) int maxTokens) {}
