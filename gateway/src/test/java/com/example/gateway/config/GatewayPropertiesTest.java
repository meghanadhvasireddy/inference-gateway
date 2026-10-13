package com.example.gateway.config;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class GatewayPropertiesTest {
    @Test void rejectsInvalidTimeoutAndRetryCount() {
        assertThrows(IllegalArgumentException.class, () -> config(Duration.ZERO, 2));
        assertThrows(IllegalArgumentException.class, () -> config(Duration.ofSeconds(1), -1));
    }

    private GatewayProperties config(Duration rpcTimeout, int retries) {
        return new GatewayProperties(List.of("worker:9090"), rpcTimeout, Duration.ofSeconds(1),
                Duration.ofSeconds(5), Duration.ofMinutes(10), retries, Duration.ofMillis(50));
    }
}
