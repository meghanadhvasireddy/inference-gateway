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

public final class InferenceWorkerService extends InferenceWorkerGrpc.InferenceWorkerImplBase {
    private static final Logger log = LoggerFactory.getLogger(InferenceWorkerService.class);
    private final String workerId;
    private final InferenceEngine engine;

    public InferenceWorkerService(String workerId, InferenceEngine engine) {
        this.workerId = workerId;
        this.engine = engine;
    }

    @Override
    public void infer(InferenceRequest request, StreamObserver<InferenceResponse> observer) {
        long start = System.nanoTime();
        try {
            if (request.getPrompt().isBlank() || request.getMaxTokens() < 1) {
                observer.onError(Status.INVALID_ARGUMENT.withDescription("prompt and max_tokens are required").asRuntimeException());
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
}
