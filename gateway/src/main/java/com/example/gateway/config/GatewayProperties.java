package com.example.gateway.config;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "inference")
public record GatewayProperties(List<String> workers, Duration rpcTimeout, Duration healthTimeout,
                                Duration healthInterval, Duration cacheTtl, int maxRetries,
                                Duration retryBaseDelay) {
    public GatewayProperties {
        if (maxRetries < 0) throw new IllegalArgumentException("inference.max-retries cannot be negative");
        requirePositive(rpcTimeout, "rpc-timeout");
        requirePositive(healthTimeout, "health-timeout");
        requirePositive(healthInterval, "health-interval");
        requirePositive(cacheTtl, "cache-ttl");
        requirePositive(retryBaseDelay, "retry-base-delay");
    }

    private static void requirePositive(Duration value, String name) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException("inference." + name + " must be positive");
        }
    }
}
