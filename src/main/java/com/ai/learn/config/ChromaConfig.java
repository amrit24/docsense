package com.ai.learn.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chroma.vectorstore.ChromaApi;
import org.springframework.ai.chroma.vectorstore.ChromaVectorStore;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * Manual ChromaDB + VectorStore wiring.
 *
 * Spring AI 1.0.1's ChromaApi uses the v2 API paths (/api/v2/...) for all
 * data operations (upsert, query, delete). ChromaDB 1.5.3 fully supports v2.
 *
 * We pre-create the collection via v2 before ChromaVectorStore.afterPropertiesSet()
 * runs, then build the store with initializeSchema=false.
 *
 * Skipped under the "test" profile — tests provide a mock VectorStore.
 */
@Slf4j
@Configuration
@Profile("!test")
public class ChromaConfig {

    private static final String DEFAULT_TENANT   = "default_tenant";
    private static final String DEFAULT_DATABASE = "default_database";

    @Value("${spring.ai.vectorstore.chroma.client.host:http://localhost}")
    private String chromaHost;

    @Value("${spring.ai.vectorstore.chroma.client.port:8000}")
    private int chromaPort;

    @Value("${spring.ai.vectorstore.chroma.collection-name:docsense-documents}")
    private String collectionName;

    @Bean
    @ConditionalOnMissingBean(ChromaApi.class)
    public ChromaApi chromaApi(ObjectMapper objectMapper) {
        String baseUrl = chromaHost + ":" + chromaPort;
        ensureCollectionExists(baseUrl);
        return new ChromaApi(baseUrl, RestClient.builder(), objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean(VectorStore.class)
    public ChromaVectorStore vectorStore(ChromaApi chromaApi, EmbeddingModel embeddingModel) {
        return ChromaVectorStore.builder(chromaApi, embeddingModel)
                .collectionName(collectionName)
                .tenantName(DEFAULT_TENANT)
                .databaseName(DEFAULT_DATABASE)
                .initializeSchema(false)
                .build();
    }

    /**
     * Ensures the collection exists using the v2 API (required for ChromaDB 1.5.3+).
     * ChromaApi's own createCollection also uses v2, but we call it here early
     * so afterPropertiesSet() finds the collection already present.
     */
    @SuppressWarnings("unchecked")
    private void ensureCollectionExists(String baseUrl) {
        RestClient client = RestClient.create(baseUrl);
        String collectionsPath = "/api/v2/tenants/{tenant}/databases/{db}/collections";

        try {
            // List collections and check if ours exists
            List<Map<String, Object>> collections = client.get()
                    .uri(collectionsPath, DEFAULT_TENANT, DEFAULT_DATABASE)
                    .retrieve()
                    .body(List.class);

            boolean exists = collections != null && collections.stream()
                    .anyMatch(c -> collectionName.equals(c.get("name")));

            if (exists) {
                log.info("ChromaDB collection '{}' already exists", collectionName);
                return;
            }
        } catch (Exception e) {
            log.warn("Could not list ChromaDB collections: {}", e.getMessage());
        }

        // Create via v2 API
        log.info("ChromaDB collection '{}' not found — creating it", collectionName);
        try {
            RestClient.create(baseUrl).post()
                    .uri(collectionsPath, DEFAULT_TENANT, DEFAULT_DATABASE)
                    .header("Content-Type", "application/json")
                    .body(Map.of(
                            "name", collectionName,
                            "metadata", Map.of("hnsw:space", "cosine")
                    ))
                    .retrieve()
                    .toBodilessEntity();
            log.info("ChromaDB collection '{}' created successfully", collectionName);
        } catch (Exception e) {
            throw new RuntimeException(
                    "Failed to create ChromaDB collection '" + collectionName +
                    "'. Is ChromaDB running at " + baseUrl + "? Error: " + e.getMessage(), e);
        }
    }
}
