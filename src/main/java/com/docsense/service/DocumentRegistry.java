package com.docsense.service;

import com.docsense.config.StorageProperties;
import com.docsense.model.DocumentRecord;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry that tracks every document ingested by this application.
 *
 * <p>Uses a {@link ConcurrentHashMap} as the in-memory working store for fast,
 * thread-safe reads, backed by a JSON file on disk so records survive restarts.
 *
 * <p>Write strategy: write-through — the JSON file is updated synchronously on
 * every {@link #register} call. This keeps the file and the map consistent at
 * all times without a background flush thread.
 *
 * <p>File location is configured via {@code storage.registry-file} in
 * {@code application.yaml} (default: {@code storage/registry.json}).
 */
@Slf4j
@Component
public class DocumentRegistry {

    private final Map<String, DocumentRecord> store = new ConcurrentHashMap<>();
    /** Maps file SHA-256 (hex) → documentId for quick duplicate lookup */
    private final Map<String, String> hashIndex = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;
    private final Path registryPath;

    public DocumentRegistry(StorageProperties storageProperties) {
        this.registryPath = Paths.get(storageProperties.getRegistryFile())
                .toAbsolutePath()
                .normalize();
        // Jackson with Java 8 time support (Instant serialization)
        this.objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    /**
     * Loads the registry from disk on startup.
     * If the file doesn't exist or is corrupt, starts with an empty registry.
     */
    @PostConstruct
    void loadFromDisk() {
        if (!Files.exists(registryPath)) {
            log.info("Registry file not found at {} — starting with empty registry.", registryPath);
            return;
        }
        try {
            List<DocumentRecord> records = objectMapper.readValue(
                    registryPath.toFile(),
                    new TypeReference<List<DocumentRecord>>() {}
            );
            records.forEach(r -> store.put(r.getDocumentId(), r));
            // populate hash index for fast lookup (records may not have sha256 if older)
            records.stream()
                    .filter(r -> r.getSha256() != null && !r.getSha256().isBlank())
                    .forEach(r -> hashIndex.put(r.getSha256(), r.getDocumentId()));
            log.info("Loaded {} document record(s) from registry file: {}", store.size(), registryPath);
        } catch (IOException e) {
            log.warn("Failed to read registry file {} — starting with empty registry. Cause: {}",
                    registryPath, e.getMessage());
        }
    }

    /**
     * Saves a document record and immediately persists the registry to disk.
     * If a record with the same ID already exists it is overwritten.
     */
    public void register(DocumentRecord record) {
        store.put(record.getDocumentId(), record);
        if (record.getSha256() != null && !record.getSha256().isBlank()) {
            hashIndex.put(record.getSha256(), record.getDocumentId());
        }
        // ensure chunk index integrity: (no-op here, chunkIds stored on record)
        log.debug("Registered document: id={} file={}", record.getDocumentId(), record.getFileName());
        saveToDisk();
    }

    /**
     * Remove a document record from the registry and persist the change.
     * Returns the removed record, or empty if not present.
     */
    public java.util.Optional<DocumentRecord> deleteById(String documentId) {
        DocumentRecord removed = store.remove(documentId);
        if (removed != null) {
            if (removed.getSha256() != null && !removed.getSha256().isBlank()) {
                hashIndex.remove(removed.getSha256());
            }
            saveToDisk();
            return java.util.Optional.of(removed);
        }
        return java.util.Optional.empty();
    }

    /**
     * Looks up a document by the SHA-256 hex of its file contents.
     *
     * @param sha256 hex-encoded sha256 string
     * @return the record, or empty if not found
     */
    public Optional<DocumentRecord> findByHash(String sha256) {
        String id = hashIndex.get(sha256);
        if (id == null) return Optional.empty();
        return Optional.ofNullable(store.get(id));
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
     * Returns the total number of indexed documents.
     */
    public int count() {
        return store.size();
    }

    // -------------------------------------------------------------------------
    // Persistence
    // -------------------------------------------------------------------------

    /**
     * Serializes the current map to JSON and writes to disk atomically.
     * Synchronized to prevent concurrent writes from corrupting the file.
     */
    private synchronized void saveToDisk() {
        try {
            Files.createDirectories(registryPath.getParent());
            objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValue(registryPath.toFile(), new ArrayList<>(store.values()));
            log.debug("Registry persisted to {}", registryPath);
        } catch (IOException e) {
            log.error("Failed to persist registry to {} — in-memory state is intact but will be lost on restart. Cause: {}",
                    registryPath, e.getMessage());
        }
    }
}
