package com.example.gateway.grpc;

import com.example.inference.proto.HealthRequest;
import com.example.inference.proto.InferenceRequest;
import com.example.inference.proto.InferenceResponse;
import com.example.inference.proto.InferenceWorkerGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public final class GrpcWorkerClient implements WorkerClient, AutoCloseable {
    private final String id;
    private final ManagedChannel channel;
    private final InferenceWorkerGrpc.InferenceWorkerBlockingStub stub;
    private final AtomicBoolean healthy = new AtomicBoolean(false);
    private final AtomicInteger active = new AtomicInteger();

    public GrpcWorkerClient(String address) {
        String[] parts = address.split(":", 2);
        if (parts.length != 2) throw new IllegalArgumentException("worker must be host:port");
        this.id = parts[0];
        this.channel = ManagedChannelBuilder.forAddress(parts[0], Integer.parseInt(parts[1])).usePlaintext().build();
        this.stub = InferenceWorkerGrpc.newBlockingStub(channel);
    }
    public String id() { return id; }
    public boolean healthy() { return healthy.get(); }
    public int active() { return active.get(); }
    public void checkHealth(Duration timeout) {
        try { healthy.set(stub.withDeadlineAfter(timeout.toMillis(), TimeUnit.MILLISECONDS)
                .health(HealthRequest.getDefaultInstance()).getHealthy()); }
        catch (RuntimeException e) { healthy.set(false); }
    }
    public InferenceResponse infer(InferenceRequest request, Duration timeout) {
        active.incrementAndGet();
        try { return stub.withDeadlineAfter(timeout.toMillis(), TimeUnit.MILLISECONDS).infer(request); }
        catch (RuntimeException e) { healthy.set(false); throw e; }
        finally { active.decrementAndGet(); }
    }
    public void close() { channel.shutdown(); }
}
