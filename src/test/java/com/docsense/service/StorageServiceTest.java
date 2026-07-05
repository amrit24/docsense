package com.docsense.service;

import com.docsense.config.StorageProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StorageServiceTest {

    @TempDir
    Path tmpDir;

    @Test
    void save_load_list_exists_and_sanitize() throws Exception {
        StorageProperties props = new StorageProperties();
        props.setDocumentsPath(tmpDir.toString());

        StorageService s = new StorageService(props);
        s.init();

        MockMultipartFile mf = new MockMultipartFile("file", "../weird/../name.pdf", "application/pdf", "hi".getBytes());
        Path saved = s.save(mf);

        assertThat(saved).exists();
        assertThat(s.exists(saved.getFileName().toString())).isTrue();
        assertThat(s.listAll()).contains(saved.getFileName().toString());

        // load existing
        var res = s.load(saved.getFileName().toString());
        assertThat(res.exists()).isTrue();

        // sanitize rejects blank
        MockMultipartFile bad = new MockMultipartFile("file", "", "application/pdf", "x".getBytes());
        assertThatThrownBy(() -> s.save(bad)).isInstanceOf(IllegalArgumentException.class);
    }
}
