package com.docsense.service;

import com.docsense.config.RagProperties;
import com.docsense.config.StorageProperties;
import com.docsense.model.DocumentRecord;
import com.docsense.model.IngestionResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.ai.vectorstore.VectorStore;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DocumentServiceTest {

    @TempDir
    Path tmpDir;

    StorageService storageService;
    VectorStore vectorStore;
    DocumentRegistry registry;
    RagProperties ragProperties;
    DocumentService documentService;

    @BeforeEach
    void init() throws Exception {
        storageService = mock(StorageService.class);
        vectorStore = mock(VectorStore.class);

        StorageProperties props = new StorageProperties();
        Path registryFile = tmpDir.resolve("registry.json");
        props.setRegistryFile(registryFile.toString());
        props.setDocumentsPath(tmpDir.resolve("documents").toString());

        registry = new DocumentRegistry(props);

        ragProperties = new RagProperties();
        ragProperties.setChunkSize(1000);
        ragProperties.setChunkOverlap(200);

        documentService = new DocumentService(storageService, vectorStore, registry, ragProperties);
    }

    @Test
    void ingest_skips_when_duplicate_hash_found() throws Exception {
        // Create a temp file and its sha256
        Path file = tmpDir.resolve("sample.pdf");
        Files.writeString(file, "hello world");

        // compute sha256 using same algorithm
        String sha256 = java.security.MessageDigest.getInstance("SHA-256")
                .digest(Files.readAllBytes(file)).toString();

        // However, DocumentService computes hex; compute hex here for registry
        byte[] digest = java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file));
        StringBuilder sb = new StringBuilder();
        for (byte b : digest) sb.append(String.format("%02x", b));
        String hex = sb.toString();

        // Register existing record with that sha
        DocumentRecord existing = DocumentRecord.builder()
                .documentId("existing-1")
                .fileName("sample.pdf")
                .storedPath(file.toString())
                .pageCount(1)
                .chunksStored(1)
                .uploadedAt(Instant.now())
                .sha256(hex)
                .build();
        registry.register(existing);

        // Mock storageService.save to write the uploaded bytes to same path
        MockMultipartFile mf = new MockMultipartFile("file", "sample.pdf", "application/pdf", "hello world".getBytes());
        when(storageService.save(mf)).thenAnswer(inv -> file);

        IngestionResponse resp = documentService.ingest(mf);

        assertThat(resp.getDocumentId()).isEqualTo("existing-1");
        assertThat(resp.getMessage()).contains("already ingested");
    }

    @Test
    void delete_removes_registry_and_attempts_vector_and_file_deletion() throws Exception {
        // Prepare stored file
        Path stored = tmpDir.resolve("to_delete.pdf");
        Files.writeString(stored, "bye");

        DocumentRecord r = DocumentRecord.builder()
                .documentId("del-1")
                .fileName("to_delete.pdf")
                .storedPath(stored.toString())
                .pageCount(1)
                .chunksStored(1)
                .uploadedAt(Instant.now())
                .chunkIds(List.of("c1", "c2"))
                .sha256("dead")
                .build();

        registry.register(r);

        boolean deleted = documentService.deleteDocument("del-1");
        assertThat(deleted).isTrue();
        assertThat(registry.findById("del-1")).isEmpty();

        // vectorStore.delete should be called with the chunkIds
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> captor = ArgumentCaptor.forClass((Class) List.class);
        verify(vectorStore).delete(captor.capture());
        assertThat(captor.getValue()).containsExactly("c1", "c2");

        // stored file should be removed
        assertThat(Files.exists(stored)).isFalse();
    }
}
