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
                                
                                **Stack:** Spring Boot · Spring AI 1.0.1 · Ollama (llama3.2 + nomic-embed-text) · ChromaDB 1.5.3
                                
                                **Workflow:**
                                1. `POST /api/v1/documents/upload` — upload a PDF
                                2. `POST /api/v1/chat` — ask a question, get an answer with source page citations
                                """)
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("Spring AI RAG Project")))
                .servers(List.of(
                        new Server().url("http://localhost:8080").description("Local development server")
                ));
    }
}
