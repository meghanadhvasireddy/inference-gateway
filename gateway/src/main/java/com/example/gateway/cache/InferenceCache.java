package com.example.gateway.cache;

import com.example.gateway.api.InferenceRequestDto;
import com.example.gateway.config.GatewayProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.dao.DataAccessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class InferenceCache {
    private static final Logger log = LoggerFactory.getLogger(InferenceCache.class);
    public record Entry(String output, String workerId, int tokenCount) {}
    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;
    private final GatewayProperties properties;

    public InferenceCache(StringRedisTemplate redis, ObjectMapper mapper, GatewayProperties properties) {
        this.redis = redis; this.mapper = mapper; this.properties = properties;
    }
    public String key(InferenceRequestDto request) {
        StringBuilder canonical = new StringBuilder();
        appendField(canonical, request.model());
        appendField(canonical, request.prompt());
        appendField(canonical, Double.toString(request.temperature()));
        appendField(canonical, Integer.toString(request.maxTokens()));
        return "inference:v2:" + hash(canonical.toString());
    }
    private static void appendField(StringBuilder key, String value) {
        key.append(value.length()).append(':').append(value);
    }
    public String promptHash(String prompt) { return hash(prompt); }
    private String hash(String input) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    public Optional<Entry> get(String key) {
        String json = redis.opsForValue().get(key);
        if (json == null) return Optional.empty();
        try { return Optional.of(mapper.readValue(json, Entry.class)); }
        catch (JsonProcessingException e) {
            try { redis.delete(key); }
            catch (DataAccessException deleteError) { log.warn("cacheKey={} corrupt_entry_delete_failed", key); }
            return Optional.empty();
        }
    }
    public void put(String key, Entry value) {
        try { redis.opsForValue().set(key, mapper.writeValueAsString(value), properties.cacheTtl()); }
        catch (JsonProcessingException e) { throw new IllegalStateException(e); }
    }
}
