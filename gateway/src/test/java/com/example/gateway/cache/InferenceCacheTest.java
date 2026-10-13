package com.example.gateway.cache;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.example.gateway.config.GatewayProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class InferenceCacheTest {
    @Test void corruptEntryBecomesMissEvenIfEvictionFails() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked") ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.get("key")).thenReturn("not-json");
        when(redis.delete("key")).thenThrow(new DataAccessResourceFailureException("redis down"));
        GatewayProperties properties = new GatewayProperties(List.of("worker:9090"), Duration.ofSeconds(2),
                Duration.ofSeconds(1), Duration.ofSeconds(5), Duration.ofMinutes(10), 2, Duration.ofMillis(50));
        InferenceCache cache = new InferenceCache(redis, new ObjectMapper(), properties);
        assertTrue(cache.get("key").isEmpty());
        verify(redis).delete("key");
    }
}
