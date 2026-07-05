package com.docsense.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("DocSense — Local PDF Q&A")
                        .description("""
                                        Upload PDF documents and ask natural-language questions about their contents.
                                        Answers are grounded strictly in the uploaded documents using Retrieval-Augmented Generation (RAG).

                                        **Stack:** Spring Boot · Spring AI 1.0.1 · Ollama (llama3.2 + nomic-embed-text) · ChromaDB

                                        **Key endpoints:**
                                        1. `POST /api/v1/documents/upload` — upload a PDF (deduplicates by SHA-256 hash)
                                        2. `DELETE /api/v1/documents/{id}` — delete document metadata, stored file, and vectors (requires chunkIds in registry)
                                        3. `POST /api/v1/chat` — ask a question, get an answer with source page citations

                                        **Notes:**
                                        - Document registry persists metadata and `sha256` to avoid re-ingestion of identical files.
                                        - A `RegistryHashMigration` utility is provided to backfill missing `sha256` values in existing registries.
                                        - Vector IDs (chunk IDs) are stored in the registry when available to support vector deletion.
                                        """)
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("Spring AI RAG Project")))
                .servers(List.of(
                        new Server().url("http://localhost:8085").description("Local development server")
                ));
    }
}
