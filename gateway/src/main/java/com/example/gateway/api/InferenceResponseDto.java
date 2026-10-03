package com.example.gateway.api;

public record InferenceResponseDto(String requestId, String model, String response, long latencyMs,
                                   boolean cached, String workerId) {}
