package com.ai.learn;

import com.ai.learn.model.ChatRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Application context load test.
 *
 * The "test" profile disables ChromaConfig so no real ChromaDB or Ollama
 * connections are attempted. All infrastructure beans are replaced with mocks.
 */
@SpringBootTest
@ActiveProfiles("test")
class DocSenseTests {

    @MockitoBean
    private VectorStore vectorStore;

    @MockitoBean
    private OllamaChatModel ollamaChatModel;

    @MockitoBean
    private OllamaEmbeddingModel ollamaEmbeddingModel;

    @Test
    void contextLoads() {
        // Verifies the Spring context wires all beans without real infrastructure
    }

    @Test
    void chatRequest_blankQuestion_shouldFailValidation() {
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

        ChatRequest request = new ChatRequest();
        request.setQuestion("");

        var violations = validator.validate(request);
        assertThat(violations).isNotEmpty();
        assertThat(violations.iterator().next().getMessage())
                .isEqualTo("Question must not be blank");
    }
}
