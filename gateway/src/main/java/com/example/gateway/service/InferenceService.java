package com.example.gateway.service;

import com.example.gateway.api.InferenceRequestDto;
import com.example.gateway.api.InferenceResponseDto;
import com.example.gateway.cache.InferenceCache;
import com.example.gateway.grpc.WorkerPool;
import com.example.gateway.model.InferenceRequestEntity;
import com.example.inference.proto.InferenceRequest;
import com.example.inference.proto.InferenceResponse;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.stereotype.Service;

@Service
public class InferenceService {
    private static final Logger log = LoggerFactory.getLogger(InferenceService.class);
    private final InferenceCache cache;
    private final WorkerPool pool;
    private final TelemetryService telemetry;
    private final MeterRegistry registry;

    public InferenceService(InferenceCache cache, WorkerPool pool, TelemetryService telemetry, MeterRegistry registry) {
        this.cache = cache; this.pool = pool; this.telemetry = telemetry; this.registry = registry;
    }

    public InferenceResponseDto infer(InferenceRequestDto request) {
        String requestId = UUID.randomUUID().toString();
        MDC.put("requestId", requestId);
        long start = System.nanoTime();
        String workerId = null;
        int tokenCount = 0;
        boolean cached = false;
        boolean success = false;
        String errorType = null;
        registry.counter("inference.requests").increment();
        try {
            String key = cache.key(request);
            Optional<InferenceCache.Entry> hit = Optional.empty();
            try { hit = cache.get(key); }
            catch (RedisConnectionFailureException e) { registry.counter("inference.cache.errors").increment(); log.warn("requestId={} cache=unavailable", requestId); }
            String output;
            if (hit.isPresent()) {
                cached = true;
                registry.counter("inference.cache.hits").increment();
                InferenceCache.Entry entry = hit.get();
                output = entry.output(); workerId = entry.workerId(); tokenCount = entry.tokenCount();
            } else {
                registry.counter("inference.cache.misses").increment();
                InferenceResponse response = pool.infer(InferenceRequest.newBuilder().setRequestId(requestId)
                        .setModel(request.model()).setPrompt(request.prompt()).setTemperature(request.temperature())
                        .setMaxTokens(request.maxTokens()).build());
                output = response.getOutput(); workerId = response.getWorkerId(); tokenCount = response.getTokenCount();
                try { cache.put(key, new InferenceCache.Entry(output, workerId, tokenCount)); }
                catch (RedisConnectionFailureException e) { registry.counter("inference.cache.errors").increment(); log.warn("requestId={} cache=write_failed", requestId); }
            }
            success = true;
            registry.counter("inference.successes").increment();
            return new InferenceResponseDto(requestId, request.model(), output, elapsedMs(start), cached, workerId);
        } catch (RuntimeException e) {
            errorType = e.getClass().getSimpleName();
            registry.counter("inference.failures").increment();
            throw e;
        } finally {
            long latencyMs = elapsedMs(start);
            Timer.builder("inference.latency").publishPercentileHistogram().register(registry)
                    .record(java.time.Duration.ofNanos(System.nanoTime() - start));
            telemetry.record(new InferenceRequestEntity(requestId, request.model(), workerId, cache.promptHash(request.prompt()),
                    latencyMs, tokenCount, cached, success, errorType));
            log.info("requestId={} workerId={} model={} latencyMs={} cached={} status={}",
                    requestId, workerId, request.model(), latencyMs, cached, success ? "success" : errorType);
            MDC.remove("requestId");
        }
    }

    private static long elapsedMs(long start) { return (System.nanoTime() - start) / 1_000_000; }
}
