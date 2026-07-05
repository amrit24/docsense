package com.ai.learn.service;

import com.ai.learn.config.StorageProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.stream.Stream;

/**
 * Handles all local disk I/O for uploaded PDF files.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Create the storage directory on startup if it doesn't exist</li>
 *   <li>Save an uploaded {@link MultipartFile} to disk</li>
 *   <li>Load a stored file as a Spring {@link Resource}</li>
 *   <li>List all stored PDF filenames</li>
 *   <li>Check whether a file has already been stored</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StorageService {

    private final StorageProperties storageProperties;

    /** Resolved absolute path to the documents storage directory. */
    private Path storageRoot;

    /**
     * Initializes the storage directory when the application starts.
     * Creates all missing parent directories if needed.
     */
    @PostConstruct
    public void init() throws IOException {
        storageRoot = Paths.get(storageProperties.getDocumentsPath()).toAbsolutePath().normalize();
        Files.createDirectories(storageRoot);
        log.info("Storage directory initialized at: {}", storageRoot);
    }

    /**
     * Saves an uploaded PDF file to the storage directory.
     *
     * <p>If a file with the same name already exists it will be overwritten,
     * ensuring re-uploads always reflect the latest version.
     *
     * @param file the multipart file to save
     * @return the {@link Path} where the file was saved
     * @throws IOException              if writing fails
     * @throws IllegalArgumentException if the filename contains path traversal characters
     */
    public Path save(MultipartFile file) throws IOException {
        String filename = sanitizeFilename(file.getOriginalFilename());
        Path destination = storageRoot.resolve(filename).normalize();

        // Guard against path traversal: destination must be inside storageRoot
        if (!destination.startsWith(storageRoot)) {
            throw new IllegalArgumentException(
                    "Filename contains illegal path traversal characters: " + filename);
        }

        Files.copy(file.getInputStream(), destination, StandardCopyOption.REPLACE_EXISTING);
        log.info("Saved PDF to disk: {}", destination);
        return destination;
    }

    /**
     * Loads a stored PDF as a Spring {@link Resource} suitable for use with
     * {@code PagePdfDocumentReader}.
     *
     * @param filename the name of the file to load
     * @return a {@link FileSystemResource} pointing to the stored file
     * @throws IllegalArgumentException if the file does not exist in storage
     */
    public Resource load(String filename) {
        Path filePath = storageRoot.resolve(sanitizeFilename(filename)).normalize();
        if (!Files.exists(filePath)) {
            throw new IllegalArgumentException("File not found in storage: " + filename);
        }
        return new FileSystemResource(filePath);
    }

    /**
     * Returns {@code true} if a PDF with the given filename is already stored.
     */
    public boolean exists(String filename) {
        return Files.exists(storageRoot.resolve(sanitizeFilename(filename)).normalize());
    }

    /**
     * Returns a list of filenames for all PDFs currently in the storage directory.
     */
    public List<String> listAll() throws IOException {
        try (Stream<Path> paths = Files.list(storageRoot)) {
            return paths
                    .filter(p -> !Files.isDirectory(p))
                    .filter(p -> p.getFileName().toString().toLowerCase().endsWith(".pdf"))
                    .map(p -> p.getFileName().toString())
                    .sorted()
                    .toList();
        }
    }

    /**
     * Returns the absolute path of the storage root directory.
     */
    public Path getStorageRoot() {
        return storageRoot;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Strips any directory components from a filename to prevent path traversal.
     * E.g. "../../etc/passwd" → "passwd"
     */
    private String sanitizeFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            throw new IllegalArgumentException("Filename must not be null or blank.");
        }
        // Take only the last path component
        return Paths.get(filename).getFileName().toString();
    }
}
