package com.example.gateway.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.example.gateway.model.InferenceRequestEntity;
import com.example.gateway.repository.InferenceRequestRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class TelemetryServiceTest {
    @Test void closeWaitsForQueuedWrites() throws Exception {
        InferenceRequestRepository repository = mock(InferenceRequestRepository.class);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(repository.save(any(InferenceRequestEntity.class))).thenAnswer(call -> {
            started.countDown();
            release.await(2, TimeUnit.SECONDS);
            return call.getArgument(0);
        });
        TelemetryService telemetry = new TelemetryService(repository, new SimpleMeterRegistry());
        telemetry.record(row("first"));
        assertTrue(started.await(1, TimeUnit.SECONDS));
        Thread closer = new Thread(telemetry::close);
        closer.start();
        assertTrue(closer.isAlive());
        release.countDown();
        closer.join(2000);
        assertFalse(closer.isAlive());
        verify(repository).save(any(InferenceRequestEntity.class));
    }

    private static InferenceRequestEntity row(String id) {
        return new InferenceRequestEntity(id, "mock-llm", "worker-1", "a".repeat(64), 1, 1, false, true, null);
    }
}
