package com.example.worker;

import com.example.inference.proto.InferenceRequest;

public interface InferenceEngine {
    String infer(InferenceRequest request) throws InterruptedException;
}
