package com.docsense.controller;

import com.docsense.model.DocumentRecord;
import com.docsense.model.IngestionResponse;
import com.docsense.service.DocumentService;
import com.docsense.service.DocumentRegistry;
import com.docsense.service.StorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/documents")
@RequiredArgsConstructor
@Tag(name = "Documents", description = "Upload and manage PDF documents for RAG indexing")
public class DocumentController {

    private final DocumentService documentService;
    private final DocumentRegistry documentRegistry;
    private final StorageService storageService;

    @Operation(
        summary = "Upload a PDF",
        description = """
            Accepts a PDF file, saves it to local disk, parses all pages, splits them into
            overlapping chunks, generates embeddings via Ollama (nomic-embed-text), and stores
            everything in ChromaDB. Returns a `documentId` that can be used to scope chat queries
            to this specific document.
            
            **Note:** Large PDFs may take 1–3 minutes to process on first upload due to embedding generation.
            """,
        responses = {
            @ApiResponse(
                responseCode = "200",
                description = "PDF ingested successfully",
                content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = IngestionResponse.class),
                    examples = @ExampleObject(value = """
                        {
                          "message": "Document uploaded successfully.",
                          "documentId": "a3f1c2d4-7e89-4b3a-bf12-3c9f01234567",
                          "fileName": "JavaDesignPatterns.pdf",
                          "pageCount": 320,
                          "chunksStored": 412,
                          "uploadedAt": "2026-07-05T10:30:00Z"
                        }
                        """)
                )
            ),
            @ApiResponse(responseCode = "400", description = "File is empty or not a PDF")
        }
    )
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<IngestionResponse> uploadDocument(
            @Parameter(description = "PDF file to upload and index", required = true)
            @RequestParam("file") MultipartFile file) throws IOException {

        validatePdfFile(file);
        IngestionResponse response = documentService.ingest(file);
        return ResponseEntity.ok(response);
    }

    @Operation(
        summary = "List all indexed documents",
        description = "Returns all documents ingested in the current application session, newest first. " +
                      "Note: the registry is in-memory and resets on application restart.",
        responses = {
            @ApiResponse(
                responseCode = "200",
                description = "List of indexed documents",
                content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    examples = @ExampleObject(value = """
                        {
                          "count": 2,
                          "documents": [
                            {
                              "documentId": "a3f1c2d4-7e89-4b3a-bf12-3c9f01234567",
                              "fileName": "JavaDesignPatterns.pdf",
                              "pageCount": 320,
                              "chunksStored": 412,
                              "uploadedAt": "2026-07-05T10:30:00Z"
                            }
                          ]
                        }
                        """)
                )
            )
        }
    )
    @GetMapping
    public ResponseEntity<Map<String, Object>> listDocuments() {
        List<DocumentRecord> records = documentRegistry.findAll();
        return ResponseEntity.ok(Map.of(
                "count", records.size(),
                "documents", records
        ));
    }

    @Operation(
        summary = "Get a document by ID",
        description = "Returns the full ingestion record for a specific document using its UUID.",
        responses = {
            @ApiResponse(
                responseCode = "200",
                description = "Document found",
                content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = DocumentRecord.class)
                )
            ),
            @ApiResponse(responseCode = "404", description = "Document not found — may have been uploaded in a previous session")
        }
    )
    @GetMapping("/{id}")
    public ResponseEntity<DocumentRecord> getDocument(
            @Parameter(description = "Document UUID returned by the upload endpoint", example = "a3f1c2d4-7e89-4b3a-bf12-3c9f01234567")
            @PathVariable String id) {
        return documentRegistry.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "Delete a document and its vectors from the index",
               description = "Deletes all chunk vectors for a document from the vector store and removes the document record from the registry.")
    @ApiResponse(responseCode = "200", description = "Document deleted",
                 content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                     examples = @ExampleObject(value = "{\n  \"message\": \"Document deleted.\",\n  \"documentId\": \"a3f1c2d4-7e89-4b3a-bf12-3c9f01234567\"\n}")))
    @ApiResponse(responseCode = "404", description = "Document not found",
                 content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                     examples = @ExampleObject(value = "{\n  \"error\": \"Document not found\"\n}")))
    @DeleteMapping("/{id}")
    public ResponseEntity<java.util.Map<String, String>> deleteDocument(
            @Parameter(description = "Document UUID to delete", required = true) @PathVariable String id) {
        boolean deleted = documentService.deleteDocument(id);
        if (deleted) {
            return ResponseEntity.ok(java.util.Map.of("message", "Document deleted.", "documentId", id));
        }
        return ResponseEntity.notFound().build();
    }

    private void validatePdfFile(MultipartFile file) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("Uploaded file is empty.");
        }
        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase().endsWith(".pdf")) {
            throw new IllegalArgumentException(
                    "Only PDF files are supported. Received: " + filename);
        }
    }
}
