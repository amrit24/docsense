package com.ai.learn.service;

import com.ai.learn.config.RagProperties;
import com.ai.learn.model.DocumentRecord;
import com.ai.learn.model.IngestionResponse;
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
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Orchestrates the full PDF ingestion pipeline:
 *
 * <pre>
 * Upload
 *   │
 *   ▼ [1] Save PDF → storage/documents/
 *   │
 *   ▼ [2] Read PDF pages  (PagePdfDocumentReader)
 *   │
 *   ▼ [3] Split into overlapping chunks  (TokenTextSplitter)
 *   │
 *   ▼ [4] Tag chunks with documentId + metadata
 *   │
 *   ▼ [5] Generate embeddings + store in ChromaDB  (VectorStore → Ollama)
 *   │
 *   ▼ [6] Register DocumentRecord  (DocumentRegistry)
 * </pre>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentIngestionService {

    private final StorageService storageService;
    private final VectorStore vectorStore;
    private final DocumentRegistry documentRegistry;
    private final RagProperties ragProperties;

    /**
     * Runs the full ingestion pipeline for an uploaded PDF.
     *
     * @param file the uploaded multipart PDF file
     * @return ingestion summary including the stable {@code documentId}
     * @throws IOException if saving or reading the file fails
     */
    public IngestionResponse ingest(MultipartFile file) throws IOException {
        String fileName = file.getOriginalFilename();
        String documentId = UUID.randomUUID().toString();
        Instant uploadedAt = Instant.now();

        log.info("=== Ingestion started | file={} | documentId={} ===", fileName, documentId);

        // ── [1] Save PDF to local disk ─────────────────────────────────────────
        Path savedPath = storageService.save(file);
        log.info("[1/5] Saved to disk: {}", savedPath);

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
        // Tagging with document_id enables precise per-document filtering at query time.
        chunks.forEach(chunk -> {
            Map<String, Object> meta = chunk.getMetadata();
            meta.put("document_id",   documentId);
            meta.put("document_name", fileName);
            meta.putIfAbsent("source", fileName);
        });
        log.info("[4/5] Tagged {} chunks with documentId={}", chunks.size(), documentId);

        // ── [5] Embed chunks and store in ChromaDB ──────────────────────────────
        // VectorStore calls OllamaEmbeddingModel (nomic-embed-text) to produce vectors,
        // then persists vectors + text + metadata to ChromaDB in one batch.
        vectorStore.add(chunks);
        log.info("[5/5] Stored {} embeddings in ChromaDB", chunks.size());

        // ── [6] Register the document record ───────────────────────────────────
        DocumentRecord record = DocumentRecord.builder()
                .documentId(documentId)
                .fileName(fileName)
                .storedPath(savedPath.toString())
                .pageCount(pageCount)
                .chunksStored(chunks.size())
                .uploadedAt(uploadedAt)
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
}
