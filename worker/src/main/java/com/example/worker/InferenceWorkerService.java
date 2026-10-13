package com.example.worker;

import com.example.inference.proto.HealthRequest;
import com.example.inference.proto.HealthResponse;
import com.example.inference.proto.InferenceRequest;
import com.example.inference.proto.InferenceResponse;
import com.example.inference.proto.InferenceWorkerGrpc;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;

public final class InferenceWorkerService extends InferenceWorkerGrpc.InferenceWorkerImplBase implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(InferenceWorkerService.class);
    private final String workerId;
    private final InferenceEngine engine;
    private final ExecutorService inferenceExecutor = Executors.newVirtualThreadPerTaskExecutor();
    private final Semaphore capacity;

    public InferenceWorkerService(String workerId, InferenceEngine engine) {
        this(workerId, engine, 256);
    }

    public InferenceWorkerService(String workerId, InferenceEngine engine, int maxInflight) {
        if (maxInflight < 1) throw new IllegalArgumentException("maxInflight must be positive");
        this.workerId = workerId;
        this.engine = engine;
        this.capacity = new Semaphore(maxInflight);
    }

    @Override
    public void infer(InferenceRequest request, StreamObserver<InferenceResponse> observer) {
        if (!capacity.tryAcquire()) {
            observer.onError(Status.RESOURCE_EXHAUSTED.withDescription("worker is at capacity").asRuntimeException());
            return;
        }
        try {
            inferenceExecutor.execute(() -> {
                try { doInfer(request, observer); }
                finally { capacity.release(); }
            });
        } catch (RuntimeException e) {
            capacity.release();
            observer.onError(Status.UNAVAILABLE.withCause(e).asRuntimeException());
        }
    }

    private void doInfer(InferenceRequest request, StreamObserver<InferenceResponse> observer) {
        long start = System.nanoTime();
        try {
            if (request.getRequestId().isBlank() || request.getPrompt().isBlank() || request.getMaxTokens() < 1
                    || !Double.isFinite(request.getTemperature()) || request.getTemperature() < 0 || request.getTemperature() > 2) {
                observer.onError(Status.INVALID_ARGUMENT.withDescription("invalid inference request").asRuntimeException());
                return;
            }
            if (!request.getModel().equals("mock-llm")) {
                observer.onError(Status.INVALID_ARGUMENT.withDescription("unsupported model").asRuntimeException());
                return;
            }
            String output = engine.infer(request);
            long latencyMs = (System.nanoTime() - start) / 1_000_000;
            observer.onNext(InferenceResponse.newBuilder().setRequestId(request.getRequestId())
                    .setWorkerId(workerId).setOutput(output).setLatencyMs(latencyMs)
                    .setTokenCount(output.isBlank() ? 0 : output.strip().split("\\s+").length).build());
            observer.onCompleted();
            log.info("requestId={} workerId={} model={} latencyMs={} status=success", request.getRequestId(), workerId, request.getModel(), latencyMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            observer.onError(Status.CANCELLED.withCause(e).asRuntimeException());
        } catch (RuntimeException e) {
            log.error("requestId={} workerId={} status=failed", request.getRequestId(), workerId, e);
            observer.onError(Status.INTERNAL.withCause(e).asRuntimeException());
        }
    }

    @Override
    public void health(HealthRequest request, StreamObserver<HealthResponse> observer) {
        observer.onNext(HealthResponse.newBuilder().setWorkerId(workerId).setHealthy(true).build());
        observer.onCompleted();
    }

    public void close() { inferenceExecutor.close(); }
}
