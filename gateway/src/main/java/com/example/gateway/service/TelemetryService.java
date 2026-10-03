package com.example.gateway.service;

import com.example.gateway.model.InferenceRequestEntity;
import com.example.gateway.repository.InferenceRequestRepository;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class TelemetryService {
    private static final Logger log = LoggerFactory.getLogger(TelemetryService.class);
    private final InferenceRequestRepository repository;
    private final MeterRegistry registry;
    private final ThreadPoolExecutor executor = new ThreadPoolExecutor(1, 2, 30, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(1000), r -> { Thread t = new Thread(r, "telemetry-writer"); t.setDaemon(true); return t; });
    public TelemetryService(InferenceRequestRepository repository, MeterRegistry registry) {
        this.repository = repository; this.registry = registry;
        registry.gauge("inference.telemetry.queue", executor.getQueue(), java.util.Collection::size);
    }
    public void record(InferenceRequestEntity entity) {
        try { executor.execute(() -> {
            try { repository.save(entity); }
            catch (RuntimeException e) { registry.counter("inference.telemetry.failures").increment(); log.error("requestId={} telemetry=failed", entity.requestId, e); }
        }); }
        catch (java.util.concurrent.RejectedExecutionException e) {
            registry.counter("inference.telemetry.dropped").increment();
            log.warn("requestId={} telemetry=dropped", entity.requestId);
        }
    }
    @PreDestroy public void close() { executor.shutdown(); }
}
