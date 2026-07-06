# DocSense — Local PDF Q&A

Ask natural-language questions about your PDF documents. Answers are grounded strictly in the content of files you upload — no hallucination, no cloud services, no API keys.

Built with **Spring Boot 3.5**, **Spring AI 1.0.1**, **Ollama**, and **ChromaDB**.

---

## Functional Overview

| Capability | Detail |
|---|---|
| Upload PDFs | POST a PDF — saved to disk, parsed, chunked, embedded, and indexed in ChromaDB |
| Ask questions | POST a natural-language question — get an answer sourced from your documents |
| Source citations | Every answer includes the exact page numbers and filenames it was drawn from |
| Scoped search | Optionally restrict a question to one specific document by its UUID |
| Multi-document | Index as many PDFs as you want; all are searched unless filtered |
| Swagger UI | Interactive API docs at `http://localhost:8085/swagger-ui.html` |
| Web UI | Built-in chat interface at `http://localhost:8085` — no separate server needed |
| Fully local | No OpenAI, no cloud APIs — Ollama + ChromaDB run entirely on your machine |

---

## Architecture

```
                        User
                          │
                          ▼
               Spring Boot REST API (:8085)
                          │
           ┌──────────────┴──────────────┐
           │                             │
           ▼                             ▼
  POST /api/v1/documents/upload   POST /api/v1/chat
           │                             │
           ▼                             ▼
    Save PDF to disk            Embed question with Ollama
    storage/documents/          (nomic-embed-text)
           │                             │
           ▼                             ▼
    Parse PDF pages             Search ChromaDB for
    (PagePdfDocumentReader)     top-K similar chunks
           │                             │
           ▼                             ▼
    Split into chunks           Retrieve top-K chunks
    (TokenTextSplitter          with page + file metadata
     1000 chars / 200 overlap)          │
           │                             │
           ▼                             ▼
    Embed each chunk            Build grounded prompt
    (Ollama nomic-embed-text)   (context + question)
           │                             │
           ▼                             ▼
    Store vectors + metadata    Ollama Chat Model
    in ChromaDB                 (llama3.2)
                                         │
                                         ▼
                               { answer, sources: [{ page, file }] }
```

### Component Roles

| Component | Version | Role |
|---|---|---|
| Spring Boot | 3.5.4 | REST API, routing, error handling |
| Spring AI | 1.0.1 | LLM/embedding abstractions, PDF reader, vector store client |
| Ollama (llama3.2) | latest | Local LLM — generates answers from retrieved context |
| Ollama (nomic-embed-text) | latest | Local embedding model — converts text to vectors |
| ChromaDB | 1.5.3 | Vector database — stores embeddings, performs cosine similarity search |
| springdoc-openapi | 2.8.6 | Swagger UI and OpenAPI spec generation |
| Tailwind CSS (CDN) | 3.x | Utility-first styling for the web UI |
| Alpine.js (CDN) | 3.x | Lightweight reactivity for the web UI |
| marked.js (CDN) | 9.x | Markdown rendering for AI responses |
| highlight.js (CDN) | 11.x | Syntax highlighting in code blocks |

### Key Design Decisions

- **RAG over fine-tuning** — the LLM only sees retrieved chunks at query time, never training data from your documents. This prevents hallucination about document content.
- **Page-level PDF reading** — one `Document` per PDF page preserves `page_number` in metadata. Chunks inherit this so citations are always accurate.
- **Cosine similarity** — ChromaDB collection is created with `hnsw:space=cosine`, optimal for semantic embedding search.
- **Temperature 0** — `temperature: 0.0` gives factual, deterministic answers rather than creative responses.
- **ChromaDB 1.5.3** — Spring AI 1.0.1's `ChromaApi` uses the v2 API paths. ChromaDB 1.5.3 is the version that supports v2 correctly with collection names (not just UUIDs).

---

## Project Structure

```
src/main/java/com/ai/learn/
├── DocSense.java
│
├── config/
│   ├── AppConfig.java                # Enables @ConfigurationProperties
│   ├── ChatClientConfig.java         # Builds ChatClient → Ollama
│   ├── ChromaConfig.java             # Pre-creates ChromaDB collection + wires VectorStore
│   ├── OpenApiConfig.java            # Swagger / OpenAPI metadata
│   ├── RagProperties.java            # rag.* yaml bindings
│   └── StorageProperties.java        # storage.* yaml bindings
│
├── controller/
│   ├── ChatController.java           # POST /api/v1/chat
│   ├── DocumentController.java       # POST /api/v1/documents/upload
│   │                                 # GET  /api/v1/documents
│   │                                 # GET  /api/v1/documents/{id}
│   └── HealthController.java         # GET  /api/v1/health
│
├── service/
│   ├── DocumentService.java # save → parse → chunk → embed → store + deletion
│   ├── DocumentRegistry.java         # JSON-backed catalog of ingested documents (persisted across restarts)
│   ├── RagQueryService.java          # embed → search → prompt → answer
│   └── StorageService.java           # Disk I/O for uploaded PDFs
│
├── model/
│   ├── ChatRequest.java              # { question, documentId? }
│   ├── ChatResponse.java             # { answer, sources[] }
│   ├── Source.java                   # { page, file }
│   ├── DocumentRecord.java           # { documentId, fileName, pageCount, chunksStored, ... }
│   └── IngestionResponse.java        # { message, documentId, fileName, ... }
│
└── exception/
    └── GlobalExceptionHandler.java   # RFC 7807 ProblemDetail error responses

src/main/resources/
├── application.yaml
└── static/
    ├── index.html                    # Single-file web UI (Tailwind + Alpine.js)
    └── favicon.svg

storage/                              # runtime — gitignored
├── documents/                        # uploaded PDFs
└── registry.json                     # persisted document registry
```

---

## How to Run

### Prerequisites

| Tool | Version | Notes |
|---|---|---|
| Java | 21 | Java 21|
| Docker | any | For ChromaDB |
| Ollama | latest | Install natively on macOS — **not** in Docker |

---

### Step 1 — Install Ollama

Download from [ollama.com/download](https://ollama.com/download) or:

```bash
brew install ollama
```

Start the server (skip if you installed the macOS app — it auto-starts in the menu bar):

```bash
ollama serve
```

Verify:

```bash
curl http://localhost:11434
```
Expected: `Ollama is running`

---

### Step 2 — Pull the required models

```bash
ollama pull llama3.2
ollama pull nomic-embed-text
```

Verify both are downloaded:

```bash
ollama list
```

---

### Step 3 — Start ChromaDB

Run this from the **project directory**:

```bash
cd /path/to/docsense
docker compose up -d
```

Verify:

```bash
curl http://localhost:8000/api/v2/heartbeat
```
Expected: `{"nanosecond heartbeat": ...}`

> ChromaDB is pinned to `1.5.3` in `docker-compose.yml`. This is required — Spring AI 1.0.1's ChromaApi uses the v2 API, and 1.5.3 is the compatible version.

---

### Step 4 — Run the app

Open the project in **IntelliJ**, confirm JDK 21 is set under `File → Project Structure → Project`, then run `DocSense.java`.

The app starts on `http://localhost:8085`. On first startup it auto-creates the `docsense-documents` collection in ChromaDB.

---

### Step 5 — Verify everything is connected

```bash
curl http://localhost:8085/api/v1/health
```

Expected:

```json
{
  "overall": "UP",
  "ollama_chat": "llama3.2",
  "ollama_embedding": "nomic-embed-text",
  "chromadb": "reachable",
  "timestamp": "..."
}
```

---

## Web UI

A built-in chat interface is available at:

```
http://localhost:8085
```

No separate server or build step needed — Spring Boot serves `src/main/resources/static/index.html` directly.

**Features:**
- Drag-and-drop or click-to-browse PDF upload with a progress indicator
- Document list in the sidebar — click any doc to scope all queries to it
- Chat panel with markdown rendering and syntax-highlighted code blocks
- Source citations rendered as pills below each answer (`filename · p.22`)
- Enter to send, Shift+Enter for new line

**Tech stack** (all loaded from CDN, zero build tooling):

| Library | Purpose |
|---|---|
| Tailwind CSS | Styling |
| Alpine.js | Reactive state and DOM updates |
| marked.js | Markdown parsing for AI responses |
| highlight.js | Syntax highlighting in code blocks |

---

## Swagger UI

Interactive API documentation is available at:

```
http://localhost:8085/swagger-ui.html
```

Raw OpenAPI spec (JSON):

```
http://localhost:8085/api-docs
```

---

## Using the API

### Upload a PDF

```bash
curl -X POST http://localhost:8085/api/v1/documents/upload \
  -F "file=@/path/to/JavaDesignPatterns.pdf"
```

Response:

```json
{
  "message": "Document uploaded successfully.",
  "documentId": "a3f1c2d4-7e89-4b3a-bf12-3c9f01234567",
  "fileName": "JavaDesignPatterns.pdf",
  "pageCount": 320,
  "chunksStored": 412,
  "uploadedAt": "2026-07-05T10:30:00Z"
}
```

Save the `documentId` — use it to scope questions to this specific document.

---

### Ask a question

```bash
curl -X POST http://localhost:8085/api/v1/chat \
  -H "Content-Type: application/json" \
  -d '{"question": "Explain the Singleton pattern."}'
```

Response:

```json
{
  "answer": "The Singleton pattern ensures that only one instance of a class exists and provides a global access point to it...",
  "sources": [
    { "page": 22, "file": "JavaDesignPatterns.pdf" },
    { "page": 23, "file": "JavaDesignPatterns.pdf" }
  ]
}
```

Scope to one document using its `documentId`:

```bash
curl -X POST http://localhost:8085/api/v1/chat \
  -H "Content-Type: application/json" \
  -d '{
    "question": "Explain the Singleton pattern.",
    "documentId": "a3f1c2d4-7e89-4b3a-bf12-3c9f01234567"
  }'
```

---

### List indexed documents

```bash
curl http://localhost:8085/api/v1/documents
```

### Delete a document

Deletes the document record and (if available) its vectors from the vector store.

```bash
curl -X DELETE http://localhost:8085/api/v1/documents/a3f1c2d4-7e89-4b3a-bf12-3c9f01234567
```

---

### Get a specific document record

```bash
curl http://localhost:8085/api/v1/documents/a3f1c2d4-7e89-4b3a-bf12-3c9f01234567
```

---

## Duplicate detection

DocSense now detects duplicate PDF uploads by computing a SHA-256 hash of each file's contents during upload. If an uploaded file's hash matches an existing record in the registry, the app skips re-ingestion (no new embeddings are created) and returns the existing `documentId` instead.

Behavior summary:
- On first upload of a file: the PDF is saved, parsed, chunked, embedded, and stored in ChromaDB as before. The computed `sha256` is stored with the document record.
- On re-upload of the identical file bytes: the server returns a friendly message and the existing `documentId`. The UI shows: "This PDF has already been uploaded — using the existing index." No new vectors are written to ChromaDB.
- Records created before this feature was added may not contain `sha256`. These records will not be matched until a migration is run (see below).

Migration utility:

To populate missing hashes for existing registry entries, a small one-time migration utility has been added: `com.docsense.tools.RegistryHashMigration`.

Run it from the project root:

```bash
./mvnw -DskipTests compile org.codehaus.mojo:exec-maven-plugin:3.1.0:java \
  -Dexec.mainClass=com.docsense.tools.RegistryHashMigration
```

What the migration does:
- Reads `storage/registry.json` and computes SHA-256 for any record missing a `sha256` field by reading the file at `storedPath`.
- Writes the updated registry back to `storage/registry.json` and creates a backup at `storage/registry.json.bak` before writing.
- Skips records where the stored file is missing and prints a message for any skipped records.

Notes and cleanup:
- The migration enables future duplicate detection but does not remove already-existing duplicate embeddings from ChromaDB. If you want to deduplicate vectors, you'll need a separate cleanup step against your ChromaDB collection (recommended only if you understand ChromaDB collection semantics and have a backup).
- The web UI will display a friendly green message when a duplicate upload is detected and will auto-hide it after a few seconds.


## Configuration

All tuning knobs are in `src/main/resources/application.yaml`:

```yaml
spring:
  ai:
    ollama:
      chat:
        options:
          model: llama3.2       # swap to mistral, phi3, gemma2:2b, etc.
          temperature: 0.0      # 0 = factual and deterministic
          num-ctx: 8192         # context window size
      embedding:
        options:
          model: nomic-embed-text

rag:
  chunk-size: 1000    # characters per chunk
  chunk-overlap: 200  # overlap between consecutive chunks
  top-k: 5           # chunks retrieved per question

storage:
  documents-path: storage/documents   # where uploaded PDFs are saved
  registry-file: storage/registry.json  # persisted document registry
```

**Swap the chat model** — any model pulled with `ollama pull` works:

```bash
ollama pull phi3        # fast, recommended for CPU-only machines
ollama pull gemma2:2b   # lightweight, very fast
ollama pull mistral     # good quality/speed balance
```

Then update `model:` in `application.yaml` and restart.

---

## Troubleshooting

**`Failed to connect to localhost port 11434`**
Ollama is not running. Open the Ollama app from Applications, or run `ollama serve`.

**`model not found`**
Pull the required models:
```bash
ollama pull llama3.2
ollama pull nomic-embed-text
```

**`Connection refused` on port 8000**
ChromaDB is not running, or you ran `docker compose` from the wrong directory:
```bash
cd /path/to/docsense
docker compose up -d
```

**`Collection docsense-documents does not exist` on startup**
The app auto-creates the collection. If this error appears, ChromaDB isn't reachable yet — wait a few seconds and restart the app.

**Health check shows `DEGRADED`**
The response body shows exactly which service failed and the error message.

**First upload is slow**
Embedding is CPU-bound. A 300-page PDF may take 1–3 minutes on Intel Mac. On Apple Silicon (M1/M2/M3) Ollama uses the Neural Engine automatically — much faster.

**Answers say "I don't have enough information"**
Either the document hasn't been uploaded, or the question uses terminology different from what's in the document. Try rephrasing, or check that the upload completed successfully.

**`documentId` not found after app restart**
Re-upload the PDF to regenerate the registry entry. This should not happen under normal circumstances — the registry is persisted to `storage/registry.json` and reloaded on startup. If the file was deleted or corrupted, re-uploading will re-index the document and restore the entry.
