package com.example.gateway.config;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "inference")
public record GatewayProperties(List<String> workers, Duration rpcTimeout, Duration healthTimeout,
                                Duration healthInterval, Duration cacheTtl, int maxRetries,
                                Duration retryBaseDelay) {}
