package com.ai.learn.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Externalized configuration for RAG pipeline tuning.
 * Values are read from application.yaml under the "rag" prefix.
 */
@Data
@Component
@ConfigurationProperties(prefix = "rag")
public class RagProperties {

    /** Maximum characters per document chunk. */
    private int chunkSize = 1000;

    /** Overlap in characters between consecutive chunks to preserve context across chunk boundaries. */
    private int chunkOverlap = 200;

    /** Number of top-k similar chunks to retrieve from the vector store per query. */
    private int topK = 5;
}
