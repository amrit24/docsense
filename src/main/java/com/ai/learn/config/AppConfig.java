package com.ai.learn.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Enables binding of externalized configuration properties.
 */
@Configuration
@EnableConfigurationProperties({RagProperties.class, StorageProperties.class})
public class AppConfig {
}
