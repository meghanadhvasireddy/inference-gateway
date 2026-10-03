package com.example.gateway.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.example.gateway.service.InferenceService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(InferenceController.class)
class InferenceControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean InferenceService service;

    @Test void validRequestReturnsResponse() throws Exception {
        when(service.infer(any())).thenReturn(new InferenceResponseDto("request-id", "mock-llm", "answer", 12, false, "worker-1"));
        mvc.perform(post("/api/v1/inference").contentType(MediaType.APPLICATION_JSON)
                .content("{\"model\":\"mock-llm\",\"prompt\":\"hello\",\"temperature\":0.7,\"maxTokens\":10}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.requestId").value("request-id"))
                .andExpect(jsonPath("$.workerId").value("worker-1"));
    }

    @Test void invalidRequestReturns400() throws Exception {
        mvc.perform(post("/api/v1/inference").contentType(MediaType.APPLICATION_JSON)
                .content("{\"model\":\"unknown\",\"prompt\":\"\",\"temperature\":3,\"maxTokens\":0}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("invalid_request"));
    }
}
