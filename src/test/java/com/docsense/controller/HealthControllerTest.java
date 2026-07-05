package com.docsense.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class HealthControllerTest {

    MockMvc mvc;

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    OllamaChatModel chatModel;

    @Mock
    VectorStore vectorStore;

    @InjectMocks
    HealthController healthController;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.standaloneSetup(healthController).build();
    }

    @Test
    void health_endpoint_reports_up_when_components_ok() throws Exception {
        when(chatModel.getDefaultOptions().getModel()).thenReturn("llama3.2");
        when(vectorStore.similaritySearch(any(org.springframework.ai.vectorstore.SearchRequest.class))).thenReturn(List.of());

        mvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.overall").value("UP"));
    }
}
