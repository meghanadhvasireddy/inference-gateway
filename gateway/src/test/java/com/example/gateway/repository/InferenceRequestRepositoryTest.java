package com.example.gateway.repository;

import static org.junit.jupiter.api.Assertions.*;

import com.example.gateway.model.InferenceRequestEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

@DataJpaTest(properties = {"spring.sql.init.mode=never", "spring.jpa.hibernate.ddl-auto=create-drop"})
class InferenceRequestRepositoryTest {
    @Autowired InferenceRequestRepository repository;

    @Test void persistsPrivacyConsciousTelemetry() {
        InferenceRequestEntity row = repository.saveAndFlush(new InferenceRequestEntity(
                "request-test", "mock-llm", "worker-2", "a".repeat(64), 42, 10, false, true, null));
        InferenceRequestEntity loaded = repository.findById(row.id).orElseThrow();
        assertEquals("request-test", loaded.requestId);
        assertEquals("a".repeat(64), loaded.promptHash);
        assertEquals("worker-2", loaded.workerId);
        assertTrue(loaded.success);
    }
}
