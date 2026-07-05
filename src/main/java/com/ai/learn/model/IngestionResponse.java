package com.ai.learn.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
@Schema(description = "Result of a PDF upload and ingestion into the vector store")
public class IngestionResponse {

    @Schema(description = "Human-readable status message", example = "Document uploaded successfully.")
    private String message;

    @Schema(
        description = "Stable UUID for this document — use as documentId in chat requests to scope queries",
        example = "a3f1c2d4-7e89-4b3a-bf12-3c9f01234567"
    )
    private String documentId;

    @Schema(description = "Original filename of the uploaded PDF", example = "JavaDesignPatterns.pdf")
    private String fileName;

    @Schema(description = "Total number of pages extracted from the PDF", example = "320")
    private int pageCount;

    @Schema(description = "Total number of text chunks stored in ChromaDB", example = "412")
    private int chunksStored;

    @Schema(description = "UTC timestamp of when the document was ingested", example = "2026-07-05T10:30:00Z")
    private Instant uploadedAt;
}
