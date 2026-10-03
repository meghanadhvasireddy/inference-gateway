package com.example.gateway.grpc;

import com.example.gateway.config.GatewayProperties;
import com.example.inference.proto.InferenceRequest;
import com.example.inference.proto.InferenceResponse;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class WorkerPool {
    private final List<WorkerClient> workers;
    private final GatewayProperties properties;
    private final MeterRegistry registry;
    private final AtomicInteger tieBreaker = new AtomicInteger();

    @Autowired
    public WorkerPool(GatewayProperties properties, MeterRegistry registry) {
        this(properties.workers().stream().map(GrpcWorkerClient::new).map(WorkerClient.class::cast).toList(), properties, registry);
    }

    WorkerPool(List<WorkerClient> workers, GatewayProperties properties, MeterRegistry registry) {
        this.workers = List.copyOf(workers);
        this.properties = properties;
        this.registry = registry;
        for (WorkerClient worker : workers) {
            registry.gauge("inference.worker.healthy", Tags.of("worker", worker.id()), worker, w -> w.healthy() ? 1 : 0);
            registry.gauge("inference.worker.active", Tags.of("worker", worker.id()), worker, WorkerClient::active);
        }
    }

    @PostConstruct
    public void initialize() { checkHealth(); }

    @Scheduled(fixedDelayString = "${inference.health-interval:5s}")
    public void checkHealth() { workers.forEach(w -> w.checkHealth(properties.healthTimeout())); }

    public WorkerClient select(Set<String> excluded) {
        int offset = Math.floorMod(tieBreaker.getAndIncrement(), Math.max(1, workers.size()));
        return java.util.stream.IntStream.range(0, workers.size())
                .mapToObj(i -> workers.get((i + offset) % workers.size()))
                .filter(w -> w.healthy() && !excluded.contains(w.id()))
                .min(Comparator.comparingInt(WorkerClient::active))
                .orElseThrow(() -> new WorkerUnavailableException("No healthy inference workers are available"));
    }

    public InferenceResponse infer(InferenceRequest request) {
        Set<String> attempted = new HashSet<>();
        RuntimeException last = null;
        for (int attempt = 0; attempt <= properties.maxRetries(); attempt++) {
            WorkerClient worker;
            try { worker = select(attempted); }
            catch (WorkerUnavailableException e) { throw new WorkerUnavailableException(e.getMessage(), last); }
            long start = System.nanoTime();
            try {
                InferenceResponse response = worker.infer(request, properties.rpcTimeout());
                Timer.builder("inference.worker.latency").tag("worker", worker.id())
                        .publishPercentileHistogram().register(registry).record(Duration.ofNanos(System.nanoTime() - start));
                Counter.builder("inference.worker.requests").tag("worker", worker.id()).register(registry).increment();
                return response;
            } catch (StatusRuntimeException e) {
                Status.Code code = e.getStatus().getCode();
                if (code == Status.Code.INVALID_ARGUMENT || code == Status.Code.PERMISSION_DENIED || code == Status.Code.UNAUTHENTICATED) throw e;
                Counter.builder("inference.worker.failures").tag("worker", worker.id()).register(registry).increment();
                if (code != Status.Code.UNAVAILABLE && code != Status.Code.DEADLINE_EXCEEDED && code != Status.Code.RESOURCE_EXHAUSTED) throw e;
                attempted.add(worker.id());
                last = e;
                if (attempt == properties.maxRetries()) break;
                registry.counter("inference.retries").increment();
                backoff(attempt);
            }
        }
        throw new WorkerUnavailableException("All attempted inference workers failed", last);
    }

    private void backoff(int attempt) {
        long base = properties.retryBaseDelay().toMillis();
        long delay = Math.min(1000, base * (1L << Math.min(attempt, 10)));
        try { Thread.sleep(ThreadLocalRandom.current().nextLong(Math.max(1, delay / 2), delay + 1)); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new WorkerUnavailableException("Interrupted during retry", e); }
    }

    @PreDestroy
    public void close() { workers.stream().filter(w -> w instanceof GrpcWorkerClient).map(w -> (GrpcWorkerClient) w).forEach(GrpcWorkerClient::close); }
}
