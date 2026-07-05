package com.ai.learn.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
@Schema(description = "Full record of an indexed document, including storage and ingestion metadata")
public class DocumentRecord {

    @Schema(description = "Stable UUID assigned at upload time", example = "a3f1c2d4-7e89-4b3a-bf12-3c9f01234567")
    private String documentId;

    @Schema(description = "Original filename as uploaded", example = "JavaDesignPatterns.pdf")
    private String fileName;

    @Schema(description = "Absolute path on disk where the PDF is stored", example = "/Users/amrit/projects/spring-ai/storage/documents/JavaDesignPatterns.pdf")
    private String storedPath;

    @Schema(description = "Total number of pages extracted from the PDF", example = "320")
    private int pageCount;

    @Schema(description = "Total number of chunks stored in ChromaDB", example = "412")
    private int chunksStored;

    @Schema(description = "UTC timestamp of when the document was ingested", example = "2026-07-05T10:30:00Z")
    private Instant uploadedAt;
}
