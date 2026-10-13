package com.example.gateway.cache;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.example.gateway.config.GatewayProperties;
import com.example.gateway.api.InferenceRequestDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class InferenceCacheTest {
    private static GatewayProperties properties() {
        return new GatewayProperties(List.of("worker:9090"), Duration.ofSeconds(2),
                Duration.ofSeconds(1), Duration.ofSeconds(5), Duration.ofMinutes(10), 2, Duration.ofMillis(50));
    }

    @Test void corruptEntryBecomesMissEvenIfEvictionFails() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked") ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.get("key")).thenReturn("not-json");
        when(redis.delete("key")).thenThrow(new DataAccessResourceFailureException("redis down"));
        InferenceCache cache = new InferenceCache(redis, new ObjectMapper(), properties());
        assertTrue(cache.get("key").isEmpty());
        verify(redis).delete("key");
    }

    @Test void fieldBoundariesCannotCollide() {
        InferenceCache cache = new InferenceCache(mock(StringRedisTemplate.class), new ObjectMapper(), properties());
        InferenceRequestDto first = new InferenceRequestDto("a\0b", "c", 0.7, 10);
        InferenceRequestDto second = new InferenceRequestDto("a", "b\0c", 0.7, 10);
        assertNotEquals(cache.key(first), cache.key(second));
        assertEquals(cache.key(first), cache.key(first));
        assertNotEquals(cache.key(first), cache.key(new InferenceRequestDto("a\0b", "c", 0.8, 10)));
    }
}
