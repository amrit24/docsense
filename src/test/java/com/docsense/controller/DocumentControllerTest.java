package com.docsense.controller;

import com.docsense.model.IngestionResponse;
import com.docsense.service.DocumentRegistry;
import com.docsense.service.DocumentService;
import com.docsense.service.StorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@ExtendWith(MockitoExtension.class)
class DocumentControllerTest {

    MockMvc mvc;

    @Mock
    DocumentService documentService;

    @Mock
    DocumentRegistry documentRegistry;

    @Mock
    StorageService storageService;

    @InjectMocks
    DocumentController documentController;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.standaloneSetup(documentController).build();
    }

    @Test
    void upload_returns_ok() throws Exception {
        MockMultipartFile mf = new MockMultipartFile("file", "file.pdf", "application/pdf", "x".getBytes());
        when(documentService.ingest(any())).thenReturn(IngestionResponse.builder().documentId("d1").message("ok").build());

        mvc.perform(multipart("/api/v1/documents/upload").file(mf).contentType(MediaType.MULTIPART_FORM_DATA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documentId").value("d1"));
    }

    @Test
    void delete_returns_not_found_when_absent() throws Exception {
        when(documentService.deleteDocument("nope")).thenReturn(false);
        mvc.perform(delete("/api/v1/documents/nope")).andExpect(status().isNotFound());
    }
}
