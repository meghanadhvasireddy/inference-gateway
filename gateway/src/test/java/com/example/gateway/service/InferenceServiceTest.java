package com.example.gateway.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.example.gateway.api.InferenceRequestDto;
import com.example.gateway.cache.InferenceCache;
import com.example.gateway.grpc.WorkerPool;
import com.example.inference.proto.InferenceResponse;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Optional;
import org.springframework.dao.DataAccessResourceFailureException;
import org.junit.jupiter.api.Test;

class InferenceServiceTest {
    private final InferenceRequestDto request = new InferenceRequestDto("mock-llm", "hello", 0.7, 10);

    @Test void cacheHitSkipsWorker() {
        InferenceCache cache = mock(InferenceCache.class);
        WorkerPool pool = mock(WorkerPool.class);
        TelemetryService telemetry = mock(TelemetryService.class);
        when(cache.key(request)).thenReturn("key");
        when(cache.get("key")).thenReturn(Optional.of(new InferenceCache.Entry("cached answer", "worker-1", 2)));
        InferenceService service = new InferenceService(cache, pool, telemetry, new SimpleMeterRegistry());
        var result = service.infer(request);
        assertTrue(result.cached());
        assertEquals("cached answer", result.response());
        verifyNoInteractions(pool);
        verify(telemetry).record(any());
    }

    @Test void missCallsWorkerAndPopulatesCache() {
        InferenceCache cache = mock(InferenceCache.class);
        WorkerPool pool = mock(WorkerPool.class);
        TelemetryService telemetry = mock(TelemetryService.class);
        when(cache.key(request)).thenReturn("key");
        when(cache.get("key")).thenReturn(Optional.empty());
        when(pool.infer(any())).thenReturn(InferenceResponse.newBuilder().setOutput("fresh answer")
                .setWorkerId("worker-2").setTokenCount(2).build());
        InferenceService service = new InferenceService(cache, pool, telemetry, new SimpleMeterRegistry());
        var result = service.infer(request);
        assertFalse(result.cached());
        assertEquals("worker-2", result.workerId());
        verify(cache).put(eq("key"), eq(new InferenceCache.Entry("fresh answer", "worker-2", 2)));
    }

    @Test void redisOutageStillUsesWorker() {
        InferenceCache cache = mock(InferenceCache.class);
        WorkerPool pool = mock(WorkerPool.class);
        TelemetryService telemetry = mock(TelemetryService.class);
        when(cache.key(request)).thenReturn("key");
        when(cache.get("key")).thenThrow(new DataAccessResourceFailureException("redis unavailable"));
        when(pool.infer(any())).thenReturn(InferenceResponse.newBuilder().setOutput("answer")
                .setWorkerId("worker-2").setTokenCount(1).build());
        InferenceService service = new InferenceService(cache, pool, telemetry, new SimpleMeterRegistry());
        assertEquals("answer", service.infer(request).response());
        verify(pool).infer(any());
    }
}
