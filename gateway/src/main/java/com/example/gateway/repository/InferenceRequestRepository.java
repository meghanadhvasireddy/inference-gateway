package com.example.gateway.repository;

import com.example.gateway.model.InferenceRequestEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InferenceRequestRepository extends JpaRepository<InferenceRequestEntity, Long> {}
