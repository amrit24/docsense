# Developer Guide — DocSense

**Audience:** Spring Boot developers with no prior AI or Spring AI knowledge.

This document explains every concept, every component, and every line of logic in this project from the ground up. After reading it, you should be able to understand, modify, and extend this codebase confidently.

---

## Table of Contents

1. [What problem does this project solve?](#1-what-problem-does-this-project-solve)
2. [AI concepts you need to know](#2-ai-concepts-you-need-to-know)
3. [How the technology stack fits together](#3-how-the-technology-stack-fits-together)
4. [Spring AI — what it is and what it does here](#4-spring-ai--what-it-is-and-what-it-does-here)
5. [Ollama — running AI models locally](#5-ollama--running-ai-models-locally)
6. [ChromaDB — the vector database](#6-chromadb--the-vector-database)
7. [The Upload Pipeline — step by step](#7-the-upload-pipeline--step-by-step)
8. [The Query Pipeline — step by step](#8-the-query-pipeline--step-by-step)
9. [Code walkthrough — every file explained](#9-code-walkthrough--every-file-explained)
10. [Web UI — frontend explained](#10-web-ui--frontend-explained)
11. [Configuration reference](#11-configuration-reference)
12. [Known limitations and future improvements](#12-known-limitations-and-future-improvements)
---

## 1. What problem does this project solve?

Imagine you have a 300-page PDF technical manual. You want to ask: *"What are the steps to configure the network adapter?"* — and get a precise answer with the exact page number.

You could use ChatGPT, but:
- You'd have to paste the entire PDF into it (often impossible — too large)
- ChatGPT might answer from its general training knowledge, not your specific document
- Your document may be confidential — you can't send it to a third-party server

This project solves all three problems:
- **Handles large documents** by splitting them into small searchable pieces
- **Answers only from your documents** — the AI is explicitly forbidden from using general knowledge
- **Runs entirely on your machine** — no data leaves your computer

---

## 2. AI concepts you need to know

You don't need a machine learning background, but you need to understand three concepts. Everything else in the project follows from these.

---

### 2.1 Embeddings — turning text into numbers

A language model cannot search text the way a database does. Instead, it converts text into a **vector** — a list of numbers (e.g., 768 numbers) that represents the *meaning* of the text.

```
"The dog chased the ball"  →  [0.21, -0.45, 0.87, 0.12, ...]
"A puppy ran after a sphere" →  [0.19, -0.43, 0.85, 0.14, ...]
"The stock market crashed"   →  [-0.72, 0.31, -0.12, 0.95, ...]
```

The first two sentences are semantically similar, so their vectors are mathematically close together. The third sentence is unrelated, so its vector is far away.

This is how semantic search works — you convert a question into a vector, then find document chunks whose vectors are closest to it.

In this project, the embedding model is **nomic-embed-text**, running locally via Ollama.

---

### 2.2 Vector Database — searching by meaning

A vector database stores embeddings and answers the question: *"given this query vector, which stored vectors are most similar?"*

This is called **similarity search** or **nearest-neighbour search**. It uses mathematical distance — specifically cosine similarity — to rank results.

In this project, **ChromaDB** is the vector database. Every chunk of every uploaded PDF is stored here as a vector, along with its original text and metadata (filename, page number).

---

### 2.3 RAG — Retrieval-Augmented Generation

RAG is the pattern that connects everything. Instead of asking an AI a question cold, you first:

1. **Retrieve** relevant chunks of text from your documents using semantic search
2. **Augment** the AI's prompt with those chunks as context
3. **Generate** an answer based only on that context

```
Without RAG:
  User: "What is the default timeout for API calls?"
  AI: "Based on my training, typical timeouts are 30 seconds..." (might be wrong for your system)

With RAG:
  User: "What is the default timeout for API calls?"
  Retrieved from your doc: "The default connection timeout is 45 seconds (page 12)"
  AI: "According to your documentation, the default timeout is 45 seconds (page 12)"
```

The AI is essentially open-book — it has the relevant pages in front of it when answering.

---

## 3. How the technology stack fits together

```
┌─────────────────────────────────────────────────────┐
│                  Your Machine                        │
│                                                      │
│  ┌─────────────────────────────────────────────┐    │
│  │         Spring Boot Application              │    │
│  │         (this project, port 8085)            │    │
│  │                                              │    │
│  │  Receives HTTP requests                      │    │
│  │  Orchestrates the RAG pipeline               │    │
│  │  Returns JSON responses                      │    │
│  └──────────────┬──────────────────────────────┘    │
│                 │                                     │
│        ┌────────┴──────────┐                         │
│        ▼                   ▼                         │
│  ┌──────────────┐   ┌──────────────────┐             │
│  │    Ollama    │   │    ChromaDB      │             │
│  │  port 11434  │   │   port 8000      │             │
│  │              │   │   (Docker)       │             │
│  │  Two models: │   │                  │             │
│  │  - llama3.2  │   │  Stores vectors  │             │
│  │    (chat)    │   │  + text chunks   │             │
│  │  - nomic-    │   │  + metadata      │             │
│  │    embed-    │   │                  │             │
│  │    text      │   │                  │             │
│  │  (embedding) │   │                  │             │
│  └──────────────┘   └──────────────────┘             │
└─────────────────────────────────────────────────────┘
```

The Spring Boot application is the **orchestrator**. It never does AI work itself — it calls Ollama for embeddings and text generation, and ChromaDB for vector storage and search.

---

## 4. Spring AI — what it is and what it does here

Spring AI is a Spring Framework library that provides clean abstractions over AI services — similar to how Spring Data provides abstractions over databases.

Without Spring AI, you'd need to:
- Write raw HTTP calls to Ollama's API
- Manually serialize/deserialize embedding vectors
- Implement the ChromaDB REST protocol yourself
- Handle batching, retries, and error handling

Spring AI provides three key abstractions used in this project:

### 4.1 `EmbeddingModel`

```java
// Conceptually, this is what Spring AI does for you:
float[] vector = embeddingModel.embed("your text here");
// Returns a 768-dimensional vector representing the text's meaning
```

In `application.yaml`, we configure which embedding model to use:
```yaml
spring.ai.ollama.embedding.options.model: nomic-embed-text
```
Spring AI automatically injects an `OllamaEmbeddingModel` bean wherever `EmbeddingModel` is needed.

### 4.2 `VectorStore`

```java
// Store documents (Spring AI calls embeddings automatically):
vectorStore.add(listOfDocuments);

// Search for similar documents:
List<Document> results = vectorStore.similaritySearch(
    SearchRequest.builder().query("your question").topK(5).build()
);
```

The `VectorStore` interface hides all the ChromaDB API complexity. Our `ChromaConfig` provides the implementation configured for our ChromaDB instance.

### 4.3 `ChatClient`

```java
// Call the LLM with a prompt:
String answer = chatClient.prompt()
    .system("You are a document assistant. Context: {context}")
    .user("What is the timeout value?")
    .call()
    .content();
```

Spring AI handles the HTTP communication with Ollama, message formatting, and response parsing.

### 4.4 `PagePdfDocumentReader`

Spring AI includes a PDF reader that extracts text page by page:
```java
PagePdfDocumentReader reader = new PagePdfDocumentReader(pdfResource, config);
List<Document> pages = reader.read();
// Each Document has getText() and getMetadata() (including "page_number")
```

### 4.5 `TokenTextSplitter`

Splits large documents into smaller chunks:
```java
TokenTextSplitter splitter = new TokenTextSplitter(1000, 200, ...);
List<Document> chunks = splitter.apply(pages);
// 1000 = max chars per chunk, 200 = overlap between chunks
```

The overlap (200 chars) is important — it ensures a sentence that spans a chunk boundary appears in both chunks, so neither loses context.

---

## 5. Ollama — running AI models locally

Ollama is a tool that lets you run large language models on your own computer. It exposes a local REST API at `http://localhost:11434`.

Think of it like a local Docker for AI models. You `ollama pull llama3.2` the same way you `docker pull nginx`.

**Two models are used in this project:**

| Model | Type | Used for | Size |
|---|---|---|---|
| `llama3.2` | Chat model | Generating answers from context | ~2GB |
| `nomic-embed-text` | Embedding model | Converting text to vectors | ~274MB |

These are separate models with different purposes. The chat model is like a "writer" — it generates coherent text. The embedding model is like a "librarian" — it converts text to searchable vectors.

On Apple Silicon (M1/M2/M3), Ollama uses the Neural Engine (GPU) automatically, making it significantly faster than CPU-only machines.

Spring AI connects to Ollama via this config:
```yaml
spring.ai.ollama.base-url: http://localhost:11434
```

---

## 6. ChromaDB — the vector database

ChromaDB is an open-source vector database. It's running in Docker in this project.

You interact with it through Spring AI's `VectorStore` abstraction, so you rarely need to think about ChromaDB directly. But it helps to know what's stored there.

Each entry in ChromaDB contains:
- **ID** — a UUID generated by Spring AI
- **Embedding** — the vector (list of ~768 floats) representing the chunk's meaning
- **Document text** — the actual text of the chunk
- **Metadata** — key-value pairs, in our case:
  - `source` — the PDF filename
  - `document_name` — the PDF filename (used for filtering)
  - `document_id` — our application-level UUID for the document
  - `page_number` — which page this chunk came from

When we search, ChromaDB compares the query vector against all stored vectors and returns the `topK` closest ones — along with their text and metadata.

**Why ChromaDB 1.5.3 specifically?**
Spring AI 1.0.1's internal `ChromaApi` class uses ChromaDB's v2 API (`/api/v2/...`). After testing, 1.5.3 is the version that works correctly with the way Spring AI passes collection names.

---

## 7. The Upload Pipeline — step by step

When you call `POST /api/v1/documents/upload` with a PDF file, here's exactly what happens:

```
Step 1: Validate
         Is the file a PDF? Is it non-empty?
         → If not: return 400 Bad Request

Step 2: Save to disk
         StorageService writes the bytes to storage/documents/filename.pdf
         → Path traversal protection: strips directory components from filename
         → Overwrites if file already exists

Step 3: Read PDF pages
         PagePdfDocumentReader reads each page as a separate Document object
         Each Document has:
           - getText()          → the text content of that page
           - getMetadata()      → { "page_number": 5, "source": "file.pdf" }
         → One Document per page preserves page number metadata

Step 4: Split into chunks
         TokenTextSplitter breaks each page's text into chunks of ~1000 chars
         with 200 chars of overlap between consecutive chunks
         Example: A 3000-char page becomes ~4 chunks
         → Overlap prevents losing context at chunk boundaries

Step 5: Tag metadata
         Each chunk gets additional metadata:
           - document_id   → UUID we generate (e.g., "a3f1-...")
           - document_name → "JavaDesignPatterns.pdf"
           - source        → "JavaDesignPatterns.pdf"

Step 6: Embed + store
         vectorStore.add(chunks) does two things:
           a) Calls OllamaEmbeddingModel to generate a vector for each chunk's text
           b) Stores each (vector + text + metadata) in ChromaDB
         → This is the slow step for large PDFs (~5-10 seconds per page on M1)

Step 7: Register
         DocumentRegistry stores a DocumentRecord in memory and persists to disk:
           { documentId, fileName, pageCount, chunksStored, uploadedAt }

Step 8: Return response
         {
           "message": "Document uploaded successfully.",
           "documentId": "a3f1c2d4-...",
           "fileName": "JavaDesignPatterns.pdf",
           "pageCount": 320,
           "chunksStored": 412,
           "uploadedAt": "2026-07-05T10:30:00Z"
         }
```

---

## 8. The Query Pipeline — step by step

When you call `POST /api/v1/chat` with a question, here's exactly what happens:

```
Step 1: Validate
         Is the question non-blank?
         → If not: return 400 Bad Request

Step 2: Embed the question
         OllamaEmbeddingModel converts the question text to a vector
         "Explain Singleton Pattern" → [0.21, -0.45, 0.87, ...]
         (This happens transparently inside vectorStore.similaritySearch)

Step 3: Search ChromaDB
         VectorStore sends the query vector to ChromaDB
         ChromaDB computes cosine similarity against all stored chunk vectors
         Returns the top-5 most similar chunks (configurable via rag.top-k)

         If documentId was provided in the request:
           → Adds a metadata filter: only search chunks where document_id = "a3f1-..."
           → This scopes the search to one specific document

Step 4: Handle empty results
         If no chunks found → return "I don't have enough information..."
         (This happens when no PDF has been uploaded yet)

Step 5: Build context string
         Retrieved chunks are formatted into a readable context block:
         """
         --- Excerpt 1 [Source: JavaDesignPatterns.pdf, Page: 22] ---
         The Singleton pattern ensures a class has only one instance...

         --- Excerpt 2 [Source: JavaDesignPatterns.pdf, Page: 23] ---
         Implementation typically uses a private constructor...
         """

Step 6: Call Ollama with grounded prompt
         System prompt (injected context):
           "Answer ONLY from the context below. Do NOT use general knowledge.
            If the context doesn't contain the answer, say 'I don't have enough information'.
            Context: {context}"
         User message: "Explain Singleton Pattern."

         → The LLM sees the actual page content and generates an answer from it

Step 7: Build citations
         Retrieved chunks are mapped to Source objects:
           { page: 22, file: "JavaDesignPatterns.pdf" }
         Duplicate pages are collapsed (multiple chunks from same page → one citation)

Step 8: Return response
         {
           "answer": "The Singleton pattern ensures...",
           "sources": [
             { "page": 22, "file": "JavaDesignPatterns.pdf" },
             { "page": 23, "file": "JavaDesignPatterns.pdf" }
           ]
         }
```

---

## 9. Code walkthrough — every file explained

### 9.1 `config/ChromaConfig.java`

**What it does:** Wires up the ChromaDB connection and ensures the collection exists before the app starts serving requests.

**Why it exists:** Spring AI 1.0.1 has a bug — `ChromaVectorStore.afterPropertiesSet()` calls `getCollection()` and throws if the collection doesn't exist, before the `initialize-schema` creation logic can run. `ChromaConfig` pre-creates the collection via the v2 REST API to work around this.

```java
@Bean
@ConditionalOnMissingBean(ChromaApi.class)
public ChromaApi chromaApi(ObjectMapper objectMapper) {
    ensureCollectionExists(baseUrl);  // ← creates collection if missing
    return new ChromaApi(baseUrl, RestClient.builder(), objectMapper);
}

@Bean
@ConditionalOnMissingBean(VectorStore.class)
public ChromaVectorStore vectorStore(ChromaApi chromaApi, EmbeddingModel embeddingModel) {
    return ChromaVectorStore.builder(chromaApi, embeddingModel)
            .initializeSchema(false)  // ← collection already exists, skip check
            .build();
}
```

`@ConditionalOnMissingBean` ensures these beans are skipped when running tests (which provide mocks instead).

`@Profile("!test")` ensures the entire class is skipped under the `test` Spring profile.

---

### 9.2 `config/ChatClientConfig.java`

```java
@Bean
public ChatClient chatClient(OllamaChatModel ollamaChatModel) {
    return ChatClient.builder(ollamaChatModel).build();
}
```

Creates the `ChatClient` bean that `RagQueryService` uses to send prompts to Ollama. Simple factory — the model configuration comes from `application.yaml`.

---

### 9.3 `config/RagProperties.java`

```java
@ConfigurationProperties(prefix = "rag")
public class RagProperties {
    private int chunkSize = 1000;
    private int chunkOverlap = 200;
    private int topK = 5;
}
```

Binds the `rag.*` properties from `application.yaml`. Injected into `DocumentIngestionService` and `RagQueryService` to make chunk size, overlap, and retrieval count configurable without code changes.

---

### 9.4 `service/StorageService.java`

Handles all disk I/O for uploaded PDFs.

Key methods:
- `init()` — runs on startup (`@PostConstruct`), creates the `storage/documents/` directory
- `save(MultipartFile)` — writes the file to disk, overwrites if exists, guards against path traversal
- `load(filename)` — returns a `FileSystemResource` for use with `PagePdfDocumentReader`
- `listAll()` — lists all `.pdf` files in the storage directory
- `sanitizeFilename()` — strips directory components to prevent `../../etc/passwd` attacks

**Why `FileSystemResource` instead of `ByteArrayResource`?**
`FileSystemResource` streams from disk — it doesn't hold the entire PDF in memory. For large files (50MB limit) this matters.

---

### 9.5 `service/DocumentIngestionService.java`

Orchestrates the 5-step ingestion pipeline (save → read → split → tag → embed+store) and registers the result in `DocumentRegistry`.

**Important detail — why page-level reading?**
```java
PdfDocumentReaderConfig.builder()
    .withPagesPerDocument(1)  // one Document per PDF page
    .build()
```
This gives each Document a `page_number` in its metadata. If we read the whole PDF as one Document, we'd lose page information and couldn't provide accurate citations.

**Important detail — chunk overlap:**
```java
new TokenTextSplitter(1000, 200, ...)
// chunkSize=1000, overlap=200
```
Consider a sentence that starts at char 980 of a page. With no overlap, it would be split mid-sentence. With 200 char overlap, chars 800-1000 of chunk N become chars 0-200 of chunk N+1, so the sentence appears complete in at least one chunk.

---

### 9.6 `service/DocumentRegistry.java`

Uses a `ConcurrentHashMap<String, DocumentRecord>` as the in-memory working store, backed by a JSON file on disk (`storage/registry.json`). Records survive application restarts.

**Write strategy — write-through:** every `register()` call updates the map and immediately flushes the full map to JSON. Simple and safe — no background thread, no risk of losing a record between flushes.

**Startup:** `@PostConstruct loadFromDisk()` reads `registry.json` if it exists and populates the map. If the file is missing or corrupt, the registry starts empty and a warning is logged — the app still starts normally.

**Thread safety:** `ConcurrentHashMap` for concurrent map reads/writes. `saveToDisk()` is `synchronized` to prevent two threads from writing the file simultaneously.

**Jackson setup:** `ObjectMapper` is configured with `JavaTimeModule` and `WRITE_DATES_AS_TIMESTAMPS=false` so `Instant` fields serialize as ISO-8601 strings (e.g. `"2026-07-05T10:30:00Z"`) rather than epoch numbers.

**`DocumentRecord` deserialization:** the model uses `@NoArgsConstructor` + `@AllArgsConstructor` alongside `@Builder` so Jackson can instantiate it via the no-args constructor and populate fields via setters (`@Data`).

---

### 9.7 `service/RagQueryService.java`

Implements the query pipeline. The most important method is `chat(ChatRequest)`.

**The system prompt:**
```java
private static final String RAG_SYSTEM_PROMPT = """
    You are a precise document assistant. Answer questions based ONLY
    on the context excerpts provided below from uploaded PDF documents.
    
    If the context does not contain enough information, respond with:
    "I don't have enough information in the uploaded documents..."
    
    Do NOT use your general training knowledge to fill gaps.
    
    Context from documents:
    {context}
    """;
```

This is the key to grounding — the `{context}` placeholder is replaced with the actual retrieved chunks. The model reads the chunks and answers from them, not from its training.

**Metadata filter for scoped search:**
```java
if (StringUtils.hasText(documentId)) {
    FilterExpressionBuilder fb = new FilterExpressionBuilder();
    builder.filterExpression(fb.eq("document_id", documentId).build());
}
```

`FilterExpressionBuilder` creates a ChromaDB metadata filter. Only chunks with `document_id == "requested-uuid"` are returned by the similarity search.

**Source deduplication:**
```java
LinkedHashMap<String, Source> seen = new LinkedHashMap<>();
chunks.forEach(doc -> {
    String key = file + "::" + page;
    seen.putIfAbsent(key, Source.builder().file(file).page(page).build());
});
```

Multiple chunks often come from the same page. `LinkedHashMap` preserves insertion order (relevance order) and `putIfAbsent` deduplicates by page.

---

### 9.8 `controller/ChatController.java`

Single endpoint: `POST /api/v1/chat`. Validates the request body with `@Valid`, delegates to `RagQueryService.chat()`, returns the response.

---

### 9.9 `controller/DocumentController.java`

Three endpoints:
- `POST /api/v1/documents/upload` — validates PDF, delegates to `DocumentIngestionService.ingest()`
- `GET /api/v1/documents` — returns all records from `DocumentRegistry`
- `GET /api/v1/documents/{id}` — looks up one record by UUID, returns 404 if not found

---

### 9.10 `controller/HealthController.java`

`GET /api/v1/health` checks three things:
1. **Ollama chat** — calls `chatModel.getDefaultOptions().getModel()` to verify the bean is wired
2. **Ollama embedding** — reads the model name from `@Value` (the embedding model doesn't expose `getDefaultOptions()` in Spring AI 1.0.x)
3. **ChromaDB** — runs a trivial `similaritySearch` with `topK=1` to verify connectivity

Returns `200 UP` if all pass, `503 DEGRADED` if any fail. The response body shows which component failed and the error message.

---

### 9.11 `exception/GlobalExceptionHandler.java`

`@RestControllerAdvice` that converts exceptions to RFC 7807 `ProblemDetail` responses:

| Exception | HTTP Status |
|---|---|
| `MethodArgumentNotValidException` | 400 — validation failed |
| `IllegalArgumentException` | 400 — bad request (e.g., non-PDF file) |
| `MaxUploadSizeExceededException` | 413 — file too large |
| `IOException` | 500 — file processing failed |
| `Exception` | 500 — unexpected error |

RFC 7807 `ProblemDetail` is the Spring 6+ standard for error responses:
```json
{
  "type": "urn:problem:bad-request",
  "title": "Bad Request",
  "status": 400,
  "detail": "Only PDF files are supported. Received: document.docx"
}
```

---

## 10. Web UI — frontend explained

The frontend lives entirely in `src/main/resources/static/index.html`. Spring Boot serves the `static/` directory automatically, so no separate web server is needed.

**No build tooling.** All libraries load from CDN:

| Library | Version | Role |
|---|---|---|
| Tailwind CSS | 3.x | Utility-class styling — no CSS files to maintain |
| Alpine.js | 3.x | Reactive state and DOM updates in ~15KB |
| marked.js | 9.x | Parses markdown in AI responses |
| highlight.js | 11.x | Syntax highlights code blocks in AI responses |

---

### Layout

```
┌─────────────────────────────────────────────────┐
│  Header: DocSense logo + doc count + API docs   │
├──────────────┬──────────────────────────────────┤
│  Sidebar     │  Chat panel                      │
│              │                                  │
│  Upload zone │  Message history                 │
│  ──────────  │  (welcome screen when empty)     │
│  Doc list    │                                  │
│              │  ──────────────────────────────  │
│  Scope pill  │  Input bar + send button         │
└──────────────┴──────────────────────────────────┘
```

---

### Alpine.js `app()` — state and methods

The entire frontend state lives in one Alpine component returned by `app()`:

```js
{
  docs:        [],       // list of DocumentRecord objects from GET /api/v1/documents
  scopedDoc:   null,     // DocumentRecord to scope chat queries to, or null for all docs
  messages:    [],       // chat history: [{ role, content, sources, loading, error }]
  question:    '',       // current textarea value
  asking:      false,    // true while waiting for /api/v1/chat response
  uploading:   false,    // true while file upload is in progress
}
```

**Key methods:**

- `loadDocs()` — calls `GET /api/v1/documents`, populates the sidebar
- `uploadFile(file)` — posts to `POST /api/v1/documents/upload`, refreshes doc list, auto-scopes to the new doc
- `sendMessage()` — pushes a user bubble, then a loading AI bubble, calls `POST /api/v1/chat`, replaces the loading bubble with the response
- `toggleScope(doc)` — sets or clears `scopedDoc`; when set, chat requests include `documentId`
- `renderMarkdown(text)` — runs `marked.parse()` to convert the AI's response to HTML

---

### Reactivity fix — why array index assignment

Alpine.js tracks changes at the array level. Mutating a nested object's property directly (e.g. `aiMsg.content = data.answer`) does not trigger a re-render. Instead, the message is replaced at its index:

```js
const aiIdx = this.messages.length;          // index of the loading bubble
this.messages.push({ ..., loading: true });  // push placeholder

// after API response:
this.messages[aiIdx] = { role: 'ai', content: data.answer, sources: data.sources, loading: false, error: false };
```

This tells Alpine the array changed, so the DOM updates correctly.

---

### Markdown and code rendering

marked.js is configured to use highlight.js for code blocks:

```js
marked.setOptions({
  highlight: (code, lang) => {
    if (lang && hljs.getLanguage(lang)) {
      return hljs.highlight(code, { language: lang }).value;
    }
    return hljs.highlightAuto(code).value;
  },
  breaks: true,   // single newline → <br>
  gfm: true,      // GitHub-flavoured markdown
});
```

The rendered HTML is injected via Alpine's `x-html` directive on the AI bubble. Loaded languages: Java, JavaScript, Python, Bash, XML.

Custom `.prose-ai` CSS classes in the `<style>` block control typography inside AI bubbles — paragraph spacing, heading sizes, inline code pills, pre/code blocks, blockquotes, and links.

---

## 11. Configuration reference

Full `application.yaml` with every property explained:

```yaml
spring:
  application:
    name: docsense

  # File upload limits
  servlet:
    multipart:
      enabled: true
      max-file-size: 50MB      # per-file limit
      max-request-size: 50MB   # total request limit

  ai:
    ollama:
      base-url: http://localhost:11434   # Ollama local server

      chat:
        options:
          model: llama3.2        # Which Ollama model handles chat
          temperature: 0.0       # 0 = most factual/deterministic, 1 = most creative
          num-ctx: 8192          # Context window: max tokens for prompt + response

      embedding:
        options:
          model: nomic-embed-text   # Which Ollama model generates embeddings

    vectorstore:
      chroma:
        client:
          host: http://localhost   # ChromaDB host (include http://)
          port: 8000               # ChromaDB port
        initialize-schema: true    # Note: doesn't actually create collection in 1.0.1
                                   #       ChromaConfig handles this instead
        collection-name: docsense-documents   # Name of the ChromaDB collection
        tenant-name: default_tenant      # ChromaDB multi-tenancy (default)
        database-name: default_database  # ChromaDB database (default)

# RAG pipeline tuning
rag:
  chunk-size: 1000     # Max characters per chunk. Smaller = more precise retrieval,
                       # larger = more context per chunk. Start with 1000.
  chunk-overlap: 200   # Chars shared between consecutive chunks.
                       # Prevents losing context at chunk boundaries.
  top-k: 5            # How many chunks to retrieve per question.
                       # More chunks = more context but slower response.

# Where uploaded PDFs are stored on disk
storage:
  documents-path: storage/documents   # Relative to working directory

server:
  port: 8085

# Swagger UI
springdoc:
  swagger-ui:
    path: /swagger-ui.html
    tags-sorter: alpha
    operations-sorter: alpha
    display-request-duration: true
  api-docs:
    path: /api-docs
```

---

## 12. Known limitations and future improvements

### ~~DocumentRegistry is in-memory~~ — resolved

**Previous behaviour:** The registry was a plain `ConcurrentHashMap` that was cleared on restart.

**Current behaviour:** The registry is backed by `storage/registry.json`. On startup, `DocumentRegistry` reads the file and repopulates the map. On every upload, the file is updated synchronously. Document records survive application restarts.

---

### Duplicate document handling

**Current behaviour:** Uploading the same PDF twice creates a second set of chunks in ChromaDB with a new `documentId`. The original chunks remain. This means search results may contain duplicates.

**Fix:** Hash the file contents on upload. If a matching hash is found in the registry, skip re-ingestion and return the existing `documentId`.

---

### No deletion support

**Current behaviour:** Once a document is indexed, its chunks cannot be removed from ChromaDB through the API.

**Fix:** Add `DELETE /api/v1/documents/{id}` that calls `vectorStore.delete(List<String> ids)` for all chunk IDs associated with that document. This requires storing chunk IDs in the registry.

---

### Chunk IDs not tracked

**Current behaviour:** When Spring AI adds chunks to ChromaDB, it generates UUIDs for each chunk internally. These IDs are not captured or stored anywhere in our application.

**Impact:** We cannot delete specific document chunks (see above).

**Fix:** Use `vectorStore.add(chunks)` and capture the returned IDs, or generate our own IDs and pass them via chunk metadata.

---

### Context window limit

**Current behaviour:** `num-ctx: 8192` means the total input to the LLM (system prompt + retrieved context + question) cannot exceed 8192 tokens (~6000 words). With `top-k: 5` and 1000-char chunks, this is rarely hit. But with `top-k: 10` and large chunks, the prompt might be truncated silently.

**Fix:** Calculate the prompt size before sending and reduce `topK` dynamically if needed.

---

### Swagger UI version conflict

**Current behaviour:** springdoc-openapi 2.8.6 is used. Versions 2.8.9 and 2.6.0 both cause `NoSuchMethodError` at runtime due to transitive swagger-annotations version conflicts with Spring AI's dependencies.

**Fix if upgrading springdoc:** Check the swagger-annotations version resolved transitively and pin it explicitly in pom.xml if needed.
