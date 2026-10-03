package com.example.gateway.grpc;

public class WorkerUnavailableException extends RuntimeException {
    public WorkerUnavailableException(String message) { super(message); }
    public WorkerUnavailableException(String message, Throwable cause) { super(message, cause); }
}
