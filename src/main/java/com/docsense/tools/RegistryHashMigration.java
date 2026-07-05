package com.docsense.tools;

import com.docsense.model.DocumentRecord;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

/**
 * One-time migration utility to compute SHA-256 for existing registry entries.
 *
 * Run from the project root:
 *
 * ./mvnw -q -Dexec.mainClass=com.docsense.tools.RegistryHashMigration \
 *   org.codehaus.mojo:exec-maven-plugin:3.1.0:java
 */
public class RegistryHashMigration {

    public static void main(String[] args) throws Exception {
        Path registryPath = Paths.get("storage/registry.json").toAbsolutePath().normalize();
        if (!Files.exists(registryPath)) {
            System.out.println("Registry file not found: " + registryPath);
            return;
        }

        ObjectMapper mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        List<DocumentRecord> records = mapper.readValue(registryPath.toFile(), new TypeReference<>() {});

        boolean changed = false;
        for (DocumentRecord r : records) {
            if (r.getSha256() != null && !r.getSha256().isBlank()) continue;
            String stored = r.getStoredPath();
            if (stored == null || stored.isBlank()) {
                System.out.println("Skipping record without storedPath: " + r.getDocumentId());
                continue;
            }
            Path file = Paths.get(stored);
            if (!Files.exists(file)) {
                System.out.println("File not found for " + r.getDocumentId() + " => " + file);
                continue;
            }
            String sha = computeSha256Hex(file);
            r.setSha256(sha);
            System.out.println("Patched " + r.getDocumentId() + " -> sha256=" + sha);
            changed = true;
        }

        if (changed) {
            Path backup = registryPath.resolveSibling(registryPath.getFileName().toString() + ".bak");
            Files.copy(registryPath, backup, StandardCopyOption.REPLACE_EXISTING);
            mapper.writerWithDefaultPrettyPrinter().writeValue(registryPath.toFile(), records);
            System.out.println("Registry updated. Backup created at: " + backup);
        } else {
            System.out.println("No records required migration.");
        }
    }

    private static String computeSha256Hex(Path path) throws IOException, NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        try (DigestInputStream dis = new DigestInputStream(Files.newInputStream(path), md)) {
            byte[] buf = new byte[8192];
            while (dis.read(buf) != -1) {
                // consume stream to update digest
            }
        }
        byte[] digest = md.digest();
        StringBuilder sb = new StringBuilder(digest.length * 2);
        for (byte b : digest) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
