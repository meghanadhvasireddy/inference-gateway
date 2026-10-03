package com.example.gateway.grpc;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.example.gateway.config.GatewayProperties;
import com.example.inference.proto.InferenceRequest;
import com.example.inference.proto.InferenceResponse;
import io.grpc.Status;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class WorkerPoolTest {
    private final GatewayProperties config = new GatewayProperties(List.of(), Duration.ofMillis(500),
            Duration.ofMillis(100), Duration.ofSeconds(5), Duration.ofMinutes(10), 2, Duration.ofMillis(1));

    private WorkerClient worker(String id, int active, boolean healthy) {
        WorkerClient w = mock(WorkerClient.class);
        when(w.id()).thenReturn(id);
        when(w.active()).thenReturn(active);
        when(w.healthy()).thenReturn(healthy);
        return w;
    }

    @Test void selectsLeastActiveHealthyWorker() {
        WorkerClient busy = worker("busy", 3, true);
        WorkerClient idle = worker("idle", 0, true);
        WorkerClient down = worker("down", 0, false);
        WorkerPool pool = new WorkerPool(List.of(busy, idle, down), config, new SimpleMeterRegistry());
        assertSame(idle, pool.select(Set.of()));
        assertSame(busy, pool.select(Set.of("idle")));
        assertThrows(WorkerUnavailableException.class, () -> pool.select(Set.of("idle", "busy")));
    }

    @Test void retriesOnAnotherWorkerAfterUnavailable() {
        WorkerClient first = worker("first", 0, true);
        WorkerClient second = worker("second", 1, true);
        InferenceRequest request = InferenceRequest.newBuilder().setRequestId("r").build();
        InferenceResponse response = InferenceResponse.newBuilder().setRequestId("r").setWorkerId("second").build();
        when(first.infer(eq(request), any())).thenThrow(Status.UNAVAILABLE.asRuntimeException());
        when(second.infer(eq(request), any())).thenReturn(response);
        SimpleMeterRegistry meters = new SimpleMeterRegistry();
        WorkerPool pool = new WorkerPool(List.of(first, second), config, meters);
        assertSame(response, pool.infer(request));
        assertEquals(1, meters.counter("inference.retries").count());
    }

    @Test void doesNotRetryInvalidArgument() {
        WorkerClient first = worker("first", 0, true);
        WorkerClient second = worker("second", 1, true);
        InferenceRequest request = InferenceRequest.getDefaultInstance();
        when(first.infer(eq(request), any())).thenThrow(Status.INVALID_ARGUMENT.asRuntimeException());
        WorkerPool pool = new WorkerPool(List.of(first, second), config, new SimpleMeterRegistry());
        assertEquals(Status.Code.INVALID_ARGUMENT, Status.fromThrowable(assertThrows(RuntimeException.class,
                () -> pool.infer(request))).getCode());
        verify(second, never()).infer(any(), any());
    }
}
