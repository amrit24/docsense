package com.ai.learn.service;

import com.ai.learn.config.RagProperties;
import com.ai.learn.model.ChatRequest;
import com.ai.learn.model.ChatResponse;
import com.ai.learn.model.Source;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * RAG query pipeline — the "Ask Question" branch of the architecture:
 *
 * <pre>
 * Ask Question
 *   │
 *   ▼ Create question embedding  (Ollama → nomic-embed-text)
 *   │
 *   ▼ Search ChromaDB for top-K similar chunks
 *   │
 *   ▼ Retrieve top-K chunks
 *   │
 *   ▼ Build prompt with context
 *   │
 *   ▼ Ollama Chat Model  (llama3.2)
 *   │
 *   ▼ Answer + Source Pages
 * </pre>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RagQueryService {

    private final VectorStore vectorStore;
    private final ChatClient chatClient;
    private final RagProperties ragProperties;

    private static final String RAG_SYSTEM_PROMPT = """
            You are a precise document assistant. Your job is to answer questions
            based ONLY on the context excerpts provided below from uploaded PDF documents.
            
            Rules:
            - Only use information present in the provided context.
            - If the context does not contain enough information to answer the question,
              respond with: "I don't have enough information in the uploaded documents to answer this question."
            - Do NOT use your general training knowledge to fill gaps.
            - Be concise and factual.
            - When citing information, mention the document name and page number.
            
            Context from documents:
            {context}
            """;

    /**
     * Answers a natural-language question using RAG over indexed PDF documents.
     *
     * @param request the question and optional documentId filter
     * @return answer text and the source pages that grounded the answer
     */
    public ChatResponse chat(ChatRequest request) {
        String question = request.getQuestion();
        log.info("RAG chat | question=\"{}\" | documentId={}", question, request.getDocumentId());

        // ── 1. Embed question → search ChromaDB for top-K similar chunks ────────
        SearchRequest searchRequest = buildSearchRequest(question, request.getDocumentId());
        List<Document> chunks = vectorStore.similaritySearch(searchRequest);
        log.info("Retrieved {} chunks from ChromaDB", chunks.size());

        if (chunks.isEmpty()) {
            return ChatResponse.builder()
                    .answer("I don't have enough information in the uploaded documents to answer this question.")
                    .sources(List.of())
                    .build();
        }

        // ── 2. Build grounded context block from retrieved chunks ────────────────
        String context = buildContext(chunks);

        // ── 3. Send to Ollama — answer is grounded strictly in the context ───────
        String answer = chatClient.prompt()
                .system(s -> s.text(RAG_SYSTEM_PROMPT).param("context", context))
                .user(question)
                .call()
                .content();

        log.info("Answer generated for question=\"{}\"", question);

        // ── 4. Build deduplicated source list ordered by relevance ───────────────
        List<Source> sources = buildSources(chunks);

        return ChatResponse.builder()
                .answer(answer)
                .sources(sources)
                .build();
    }

    // ── Private helpers ──────────────────────────────────────────────────────────

    /**
     * Builds a {@link SearchRequest} that embeds the question and retrieves top-K chunks.
     * If a {@code documentId} is provided, restricts retrieval to that document only.
     */
    private SearchRequest buildSearchRequest(String question, String documentId) {
        SearchRequest.Builder builder = SearchRequest.builder()
                .query(question)
                .topK(ragProperties.getTopK());

        if (StringUtils.hasText(documentId)) {
            FilterExpressionBuilder fb = new FilterExpressionBuilder();
            builder.filterExpression(fb.eq("document_id", documentId).build());
            log.debug("Scoping search to documentId={}", documentId);
        }

        return builder.build();
    }

    /**
     * Formats retrieved chunks into a numbered context block for the prompt.
     * Each entry shows the source file, page, and chunk text.
     */
    private String buildContext(List<Document> chunks) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < chunks.size(); i++) {
            Document doc = chunks.get(i);
            Map<String, Object> meta = doc.getMetadata();
            String source = (String) meta.getOrDefault("source", "Unknown");
            String page   = meta.getOrDefault("page_number", "N/A").toString();

            sb.append("--- Excerpt ").append(i + 1)
              .append(" [Source: ").append(source)
              .append(", Page: ").append(page).append("] ---\n")
              .append(doc.getText())
              .append("\n\n");
        }
        return sb.toString();
    }

    /**
     * Converts retrieved chunks into a deduplicated {@link Source} list.
     *
     * <p>Multiple chunks from the same page of the same file are collapsed into
     * one source entry (LinkedHashMap preserves insertion/relevance order).
     * Result: sources ordered by relevance, no duplicates.
     */
    private List<Source> buildSources(List<Document> chunks) {
        // key = "file::page" — deduplicates multiple chunks from the same page
        LinkedHashMap<String, Source> seen = new LinkedHashMap<>();

        chunks.forEach(doc -> {
            Map<String, Object> meta = doc.getMetadata();
            String file = (String) meta.getOrDefault("source", "Unknown");
            int    page = parsePageNumber(meta.get("page_number"));
            String key  = file + "::" + page;

            seen.putIfAbsent(key, Source.builder()
                    .file(file)
                    .page(page)
                    .build());
        });

        return seen.values().stream().collect(Collectors.toList());
    }

    private int parsePageNumber(Object raw) {
        if (raw == null) return 0;
        try { return Integer.parseInt(raw.toString()); }
        catch (NumberFormatException e) { return 0; }
    }
}
