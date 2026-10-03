package com.example.worker;

import com.example.inference.proto.InferenceRequest;
import java.util.concurrent.ThreadLocalRandom;

public final class MockInferenceEngine implements InferenceEngine {
    @Override
    public String infer(InferenceRequest request) throws InterruptedException {
        Thread.sleep(ThreadLocalRandom.current().nextLong(60, 201));
        String answer = "Mock inference for '" + request.getPrompt().strip() + "'. "
                + "This worker simulates a model; replace InferenceEngine for a real provider.";
        String[] words = answer.split("\\s+");
        if (words.length <= request.getMaxTokens()) return answer;
        return String.join(" ", java.util.Arrays.copyOf(words, request.getMaxTokens()));
    }
}
