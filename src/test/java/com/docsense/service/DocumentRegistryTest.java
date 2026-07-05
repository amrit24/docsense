package com.docsense.service;

import com.docsense.config.StorageProperties;
import com.docsense.model.DocumentRecord;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentRegistryTest {

    @TempDir
    Path tmpDir;

    @Test
    void register_findByHash_and_delete() throws Exception {
        StorageProperties props = new StorageProperties();
        Path registryFile = tmpDir.resolve("registry.json");
        props.setRegistryFile(registryFile.toString());

        DocumentRegistry registry = new DocumentRegistry(props);

        DocumentRecord r = DocumentRecord.builder()
                .documentId("doc-1")
                .fileName("a.pdf")
                .storedPath(tmpDir.resolve("a.pdf").toString())
                .pageCount(1)
                .chunksStored(1)
                .uploadedAt(Instant.now())
                .sha256("deadbeef")
                .build();

        registry.register(r);

        assertThat(registry.findById("doc-1")).isPresent();
        assertThat(registry.findByHash("deadbeef")).isPresent();

        var deleted = registry.deleteById("doc-1");
        assertThat(deleted).isPresent();
        assertThat(registry.findById("doc-1")).isEmpty();
        assertThat(registry.findByHash("deadbeef")).isEmpty();
    }
}
