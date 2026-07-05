package com.docsense.service;

import com.docsense.config.RagProperties;
import com.docsense.model.ChatRequest;
import com.docsense.model.ChatResponse;
import com.docsense.model.Source;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class RagQueryServiceTest {

    VectorStore vectorStore;
    ChatClient chatClient;
    RagProperties ragProperties;
    RagQueryService ragQueryService;

    @BeforeEach
    void init() {
        vectorStore = Mockito.mock(VectorStore.class);
        chatClient = Mockito.mock(ChatClient.class, Mockito.RETURNS_DEEP_STUBS);
        ragProperties = new RagProperties();
        ragProperties.setTopK(3);
        ragQueryService = new RagQueryService(vectorStore, chatClient, ragProperties);
    }

    @Test
    void chat_returns_not_enough_info_when_no_chunks() {
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

        ChatRequest req = new ChatRequest();
        req.setQuestion("What is X?");

        ChatResponse resp = ragQueryService.chat(req);
        assertThat(resp.getAnswer()).contains("I don't have enough information");
        assertThat(resp.getSources()).isEmpty();
    }

    @Test
    void buildContext_and_buildSources_helpers_work() throws Exception {
        Document d1 = new Document("text1", Map.of("source", "A.pdf", "page_number", 1));
        Document d2 = new Document("text2", Map.of("source", "A.pdf", "page_number", 2));

        // Use reflection to call private helpers
        var buildContext = RagQueryService.class.getDeclaredMethod("buildContext", List.class);
        buildContext.setAccessible(true);
        String context = (String) buildContext.invoke(ragQueryService, List.of(d1, d2));
        assertThat(context).contains("Excerpt 1").contains("text1");

        var buildSources = RagQueryService.class.getDeclaredMethod("buildSources", List.class);
        buildSources.setAccessible(true);
        @SuppressWarnings("unchecked")
        var sources = (List<?>) buildSources.invoke(ragQueryService, List.of(d1, d2));
        assertThat(sources).hasSize(2);
    }
}
