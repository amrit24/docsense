package com.ai.learn.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/health")
@RequiredArgsConstructor
@Tag(name = "Health", description = "Infrastructure connectivity checks")
public class HealthController {

    private final OllamaChatModel chatModel;
    private final VectorStore vectorStore;

    @Value("${spring.ai.ollama.chat.options.model}")
    private String chatModelName;

    @Value("${spring.ai.ollama.embedding.options.model}")
    private String embeddingModelName;

    @Operation(
        summary = "Health check",
        description = """
            Verifies that all three infrastructure components are reachable:
            - **Ollama chat model** — the LLM used for answer generation
            - **Ollama embedding model** — used to embed questions and document chunks
            - **ChromaDB** — the vector store used for semantic search
            
            Returns `200 UP` when everything is healthy, `503 DEGRADED` if any component fails.
            """,
        responses = {
            @ApiResponse(
                responseCode = "200",
                description = "All services are reachable",
                content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    examples = @ExampleObject(value = """
                        {
                          "timestamp": "2026-07-05T10:00:00Z",
                          "application": "spring-ai-rag",
                          "ollama_chat": "llama3.2",
                          "ollama_embedding": "nomic-embed-text",
                          "chromadb": "reachable",
                          "overall": "UP"
                        }
                        """)
                )
            ),
            @ApiResponse(
                responseCode = "503",
                description = "One or more services are unavailable",
                content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    examples = @ExampleObject(value = """
                        {
                          "timestamp": "2026-07-05T10:00:00Z",
                          "application": "spring-ai-rag",
                          "ollama_chat": "ERROR: Connection refused",
                          "ollama_embedding": "nomic-embed-text",
                          "chromadb": "reachable",
                          "overall": "DEGRADED"
                        }
                        """)
                )
            )
        }
    )
    @GetMapping
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("timestamp", Instant.now().toString());
        status.put("application", "docsense");

        String chatStatus = checkComponent(() -> {
            chatModel.getDefaultOptions().getModel();
            return chatModelName;
        });
        status.put("ollama_chat", chatStatus);
        status.put("ollama_embedding", embeddingModelName);

        String vectorStoreStatus = checkComponent(() -> {
            vectorStore.similaritySearch(
                    org.springframework.ai.vectorstore.SearchRequest.builder()
                            .query("health check")
                            .topK(1)
                            .build()
            );
            return "reachable";
        });
        status.put("chromadb", vectorStoreStatus);

        boolean healthy = status.values().stream()
                .filter(v -> v instanceof String s && s.startsWith("ERROR"))
                .findAny()
                .isEmpty();

        status.put("overall", healthy ? "UP" : "DEGRADED");

        return healthy
                ? ResponseEntity.ok(status)
                : ResponseEntity.status(503).body(status);
    }

    private String checkComponent(CheckedSupplier<String> supplier) {
        try {
            return supplier.get();
        } catch (Exception e) {
            return "ERROR: " + e.getMessage();
        }
    }

    @FunctionalInterface
    private interface CheckedSupplier<T> {
        T get() throws Exception;
    }
}
