package com.example.gateway.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "inference_requests")
public class InferenceRequestEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    @Column(name = "request_id", nullable = false, unique = true) public String requestId;
    @Column(nullable = false) public String model;
    @Column(name = "worker_id") public String workerId;
    @Column(name = "prompt_hash", nullable = false, length = 64) public String promptHash;
    @Column(name = "latency_ms", nullable = false) public long latencyMs;
    @Column(name = "token_count", nullable = false) public int tokenCount;
    @Column(nullable = false) public boolean cached;
    @Column(nullable = false) public boolean success;
    @Column(name = "error_type") public String errorType;
    @Column(name = "created_at", nullable = false) public Instant createdAt = Instant.now();

    protected InferenceRequestEntity() {}
    public InferenceRequestEntity(String requestId, String model, String workerId, String promptHash,
                                  long latencyMs, int tokenCount, boolean cached, boolean success, String errorType) {
        this.requestId = requestId; this.model = model; this.workerId = workerId; this.promptHash = promptHash;
        this.latencyMs = latencyMs; this.tokenCount = tokenCount; this.cached = cached;
        this.success = success; this.errorType = errorType;
    }
}
