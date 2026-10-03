package com.example.worker;

import io.grpc.Server;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public final class WorkerApplication {
    private WorkerApplication() {}

    public static void main(String[] args) throws Exception {
        String workerId = System.getenv().getOrDefault("WORKER_ID", "worker-local");
        int port = Integer.parseInt(System.getenv().getOrDefault("WORKER_PORT", "9090"));
        Server server = NettyServerBuilder.forPort(port)
                .executor(Executors.newFixedThreadPool(Math.max(4, Runtime.getRuntime().availableProcessors())))
                .addService(new InferenceWorkerService(workerId, new MockInferenceEngine())).build().start();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            server.shutdown();
            try { server.awaitTermination(10, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }));
        System.out.printf("workerId=%s port=%d status=started%n", workerId, port);
        server.awaitTermination();
    }
}
