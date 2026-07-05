package com.docsense.service;

import com.docsense.model.DocumentRecord;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory registry that tracks every document ingested during the application session.
 *
 * <p>Keyed by {@code documentId} (UUID). Because this is in-memory, the registry is
 * cleared on restart — the PDFs and their ChromaDB embeddings persist on disk, but
 * the registry metadata does not. For production use, replace with a database-backed store.
 * TODO : Call db from here to store document matadata
 */
@Slf4j
@Component
public class DocumentRegistry {

    private final Map<String, DocumentRecord> store = new ConcurrentHashMap<>();

    /**
     * Saves a document record. If a record with the same ID already exists it is overwritten.
     */
    public void register(DocumentRecord record) {
        store.put(record.getDocumentId(), record);
        log.debug("Registered document: id={} file={}", record.getDocumentId(), record.getFileName());
    }

    /**
     * Looks up a document by its ID.
     *
     * @param documentId the UUID assigned at upload time
     * @return the record, or empty if not found
     */
    public Optional<DocumentRecord> findById(String documentId) {
        return Optional.ofNullable(store.get(documentId));
    }

    /**
     * Returns all registered documents, ordered by upload time (newest first).
     */
    public List<DocumentRecord> findAll() {
        List<DocumentRecord> records = new ArrayList<>(store.values());
        records.sort((a, b) -> b.getUploadedAt().compareTo(a.getUploadedAt()));
        return Collections.unmodifiableList(records);
    }

    /**
     * Returns the total number of indexed documents in this session.
     */
    public int count() {
        return store.size();
    }
}
