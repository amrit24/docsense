package com.ai.learn.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Externalized configuration for local file storage.
 * Bound from application.yaml under the "storage" prefix.
 */
@Data
@Component
@ConfigurationProperties(prefix = "storage")
public class StorageProperties {

    /**
     * Directory path (relative to the working directory, or absolute)
     * where uploaded PDF files are persisted.
     * Default: storage/documents
     */
    private String documentsPath = "storage/documents";
}
