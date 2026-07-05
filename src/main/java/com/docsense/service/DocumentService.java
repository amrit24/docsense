package com.docsense.service;

import com.docsense.config.RagProperties;
import com.docsense.model.DocumentRecord;
import com.docsense.model.IngestionResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.pdf.config.PdfDocumentReaderConfig;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.time.Instant;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static java.nio.file.Files.newInputStream;

/**
 * Orchestrates the full PDF ingestion and document lifecycle operations.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentService {

    private final StorageService storageService;
    private final VectorStore vectorStore;
    private final DocumentRegistry documentRegistry;
    private final RagProperties ragProperties;

    public IngestionResponse ingest(MultipartFile file) throws IOException {
        String fileName = file.getOriginalFilename();
        String documentId = UUID.randomUUID().toString();
        Instant uploadedAt = Instant.now();

        log.info("=== Ingestion started | file={} | documentId={} ===", fileName, documentId);

        // ── [1] Save PDF to local disk ─────────────────────────────────────────
        Path savedPath = storageService.save(file);
        log.info("[1/5] Saved to disk: {}", savedPath);

        // ── [1.5] Compute file hash and check for duplicates ───────────────────
        String sha256 = computeSha256Hex(savedPath);
        documentRegistry.findByHash(sha256).ifPresent(existing -> {
            log.info("Duplicate upload detected — existing documentId={} (file={})", existing.getDocumentId(), existing.getFileName());
        });
        var existingOpt = documentRegistry.findByHash(sha256);
        if (existingOpt.isPresent()) {
            DocumentRecord existing = existingOpt.get();
            return IngestionResponse.builder()
                    .message("Document already ingested. Returning existing documentId.")
                    .documentId(existing.getDocumentId())
                    .fileName(existing.getFileName())
                    .pageCount(existing.getPageCount())
                    .chunksStored(existing.getChunksStored())
                    .uploadedAt(existing.getUploadedAt())
                    .build();
        }

        // ── [2] Read PDF pages from saved file ─────────────────────────────────
        Resource pdfResource = storageService.load(fileName);

        PdfDocumentReaderConfig readerConfig = PdfDocumentReaderConfig.builder()
                .withPagesPerDocument(1)   // one Document per page preserves page_number in metadata
                .build();

        PagePdfDocumentReader pdfReader = new PagePdfDocumentReader(pdfResource, readerConfig);
        List<Document> pages = pdfReader.read();
        int pageCount = pages.size();
        log.info("[2/5] Read {} pages from {}", pageCount, fileName);

        // ── [3] Split pages into overlapping chunks ─────────────────────────────
        TokenTextSplitter splitter = new TokenTextSplitter(
                ragProperties.getChunkSize(),
                ragProperties.getChunkOverlap(),
                5,       // min chars — discard tiny fragments
                10000,   // max chars per chunk
                true     // keep separator tokens
        );
        List<Document> chunks = splitter.apply(pages);
        log.info("[3/5] Split into {} chunks (chunkSize={}, overlap={})",
                chunks.size(), ragProperties.getChunkSize(), ragProperties.getChunkOverlap());

        // ── [4] Tag every chunk with documentId and source metadata ─────────────
        chunks.forEach(chunk -> {
            Map<String, Object> meta = chunk.getMetadata();
            meta.put("document_id",   documentId);
            meta.put("document_name", fileName);
            meta.putIfAbsent("source", fileName);
        });
        log.info("[4/5] Tagged {} chunks with documentId={}", chunks.size(), documentId);

        // ── [5] Embed chunks and store in ChromaDB ──────────────────────────────
        // Persist embeddings to the vector store.
        // NOTE: Spring AI's VectorStore.add(...) returns void in this version,
        // so we cannot capture vector IDs here. If a future VectorStore API
        // returns the created IDs, capture them and store in DocumentRecord.chunkIds.
        vectorStore.add(chunks);
        log.info("[5/5] Stored {} embeddings in ChromaDB", chunks.size());
        java.util.List<String> chunkIds = java.util.Collections.emptyList();

        // ── [6] Register the document record ───────────────────────────────────
        DocumentRecord record = DocumentRecord.builder()
                .documentId(documentId)
                .fileName(fileName)
                .storedPath(savedPath.toString())
                .pageCount(pageCount)
                .chunksStored(chunks.size())
                .chunkIds(chunkIds)
                .uploadedAt(uploadedAt)
                .sha256(sha256)
                .build();
        documentRegistry.register(record);

        log.info("=== Ingestion complete | file={} | documentId={} | chunks={} ===",
                fileName, documentId, chunks.size());

        return IngestionResponse.builder()
                .message("Document uploaded successfully.")
                .documentId(documentId)
                .fileName(fileName)
                .pageCount(pageCount)
                .chunksStored(chunks.size())
                .uploadedAt(uploadedAt)
                .build();
    }

    private String computeSha256Hex(Path path) throws IOException {
        try (InputStream in = newInputStream(path)) {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            try (DigestInputStream dis = new DigestInputStream(in, md)) {
                dis.transferTo(OutputStream.nullOutputStream());
            }
            byte[] digest = md.digest();
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    /**
     * Deletes a document from the vector store and registry.
     * Returns {@code true} if a document was found and deleted, {@code false} if not found.
     */
    public boolean deleteDocument(String documentId) {
        var opt = documentRegistry.findById(documentId);
        if (opt.isEmpty()) return false;
        DocumentRecord record = opt.get();

        if (record.getChunkIds() != null && !record.getChunkIds().isEmpty()) {
            try {
                vectorStore.delete(record.getChunkIds());
            } catch (Exception e) {
                log.warn("Failed to delete vectors for document {}: {}", documentId, e.getMessage());
            }
        }

        documentRegistry.deleteById(documentId);

        try {
            if (record.getStoredPath() != null && !record.getStoredPath().isBlank()) {
                java.nio.file.Files.deleteIfExists(java.nio.file.Paths.get(record.getStoredPath()));
            }
        } catch (Exception e) {
            log.warn("Failed to delete stored file for document {}: {}", documentId, e.getMessage());
        }

        return true;
    }
}
