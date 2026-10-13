package com.example.worker;

import static org.junit.jupiter.api.Assertions.*;

import com.example.inference.proto.HealthRequest;
import com.example.inference.proto.HealthResponse;
import com.example.inference.proto.InferenceRequest;
import com.example.inference.proto.InferenceResponse;
import io.grpc.stub.StreamObserver;
import io.grpc.Status;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class InferenceWorkerServiceTest {
    @Test void healthRespondsWhileInferenceIsBlocked() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try (InferenceWorkerService service = new InferenceWorkerService("worker-test", request -> {
            started.countDown(); release.await(2, TimeUnit.SECONDS); return "answer";
        })) {
            AtomicReference<InferenceResponse> response = new AtomicReference<>();
            service.infer(InferenceRequest.newBuilder().setRequestId("test-id").setModel("mock-llm")
                    .setPrompt("prompt").setMaxTokens(10).build(), observer(response));
            assertTrue(started.await(1, TimeUnit.SECONDS));
            AtomicReference<HealthResponse> health = new AtomicReference<>();
            service.health(HealthRequest.getDefaultInstance(), observer(health));
            assertTrue(health.get().getHealthy());
            release.countDown();
            for (int i = 0; i < 100 && response.get() == null; i++) Thread.sleep(10);
            assertEquals("test-id", response.get().getRequestId());
        } finally { release.countDown(); }
    }

    @Test void rejectsInvalidTemperatureBeforeCallingEngine() throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<>();
        try (InferenceWorkerService service = new InferenceWorkerService("worker-test", request -> {
            throw new AssertionError("engine must not run");
        })) {
            service.infer(InferenceRequest.newBuilder().setRequestId("r").setModel("mock-llm")
                    .setPrompt("hello").setMaxTokens(10).setTemperature(Double.NaN).build(), new StreamObserver<>() {
                public void onNext(InferenceResponse value) { fail("unexpected response"); }
                public void onError(Throwable throwable) { error.set(throwable); done.countDown(); }
                public void onCompleted() { fail("unexpected completion"); }
            });
            assertTrue(done.await(1, TimeUnit.SECONDS));
            assertEquals(Status.Code.INVALID_ARGUMENT, Status.fromThrowable(error.get()).getCode());
        }
    }

    @Test void returnsResourceExhaustedAtCapacity() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch rejected = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<>();
        InferenceRequest request = InferenceRequest.newBuilder().setRequestId("r").setModel("mock-llm")
                .setPrompt("hello").setMaxTokens(10).build();
        try (InferenceWorkerService service = new InferenceWorkerService("worker-test", r -> {
            started.countDown(); release.await(2, TimeUnit.SECONDS); return "answer";
        }, 1)) {
            service.infer(request, observer(new AtomicReference<>()));
            assertTrue(started.await(1, TimeUnit.SECONDS));
            service.infer(request, new StreamObserver<>() {
                public void onNext(InferenceResponse value) { fail("unexpected response"); }
                public void onError(Throwable throwable) { error.set(throwable); rejected.countDown(); }
                public void onCompleted() { fail("unexpected completion"); }
            });
            assertTrue(rejected.await(1, TimeUnit.SECONDS));
            assertEquals(Status.Code.RESOURCE_EXHAUSTED, Status.fromThrowable(error.get()).getCode());
        } finally { release.countDown(); }
    }

    private static <T> StreamObserver<T> observer(AtomicReference<T> result) {
        return new StreamObserver<>() {
            public void onNext(T value) { result.set(value); }
            public void onError(Throwable error) { throw new AssertionError(error); }
            public void onCompleted() {}
        };
    }
}
