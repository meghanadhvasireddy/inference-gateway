package com.example.gateway.grpc;

import com.example.inference.proto.InferenceRequest;
import com.example.inference.proto.InferenceResponse;
import java.time.Duration;

public interface WorkerClient {
    String id();
    boolean healthy();
    int active();
    void checkHealth(Duration timeout);
    InferenceResponse infer(InferenceRequest request, Duration timeout);
}
