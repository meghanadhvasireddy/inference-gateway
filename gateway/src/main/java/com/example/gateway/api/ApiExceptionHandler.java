package com.example.gateway.api;

import com.example.gateway.grpc.WorkerUnavailableException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Map<String, String>> invalid(MethodArgumentNotValidException e) {
        return ResponseEntity.badRequest().body(Map.of("error", "invalid_request", "message", "Check model, prompt, temperature, and maxTokens"));
    }
    @ExceptionHandler(WorkerUnavailableException.class)
    ResponseEntity<Map<String, String>> unavailable(WorkerUnavailableException e) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("error", "workers_unavailable", "message", e.getMessage()));
    }
}
