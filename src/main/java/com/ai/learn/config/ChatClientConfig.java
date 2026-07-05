package com.ai.learn.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Provides a configured ChatClient bean that targets the local Ollama chat model.
 */
@Configuration
public class ChatClientConfig {

    /**
     * Creates a Spring AI ChatClient backed by Ollama.
     * The model and options are resolved from application.yaml.
     */
    @Bean
    public ChatClient chatClient(OllamaChatModel ollamaChatModel) {
        return ChatClient.builder(ollamaChatModel).build();
    }
}
