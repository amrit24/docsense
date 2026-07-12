# Changelog — DocSense

All notable changes to this project are documented here.

---

## [0.1.0] - 2026-07-06

### ✨ Major Feature: Full Containerization

The entire DocSense application is now fully containerized. All three components (Ollama, ChromaDB, and Spring Boot) run in Docker containers orchestrated by a single `docker-compose.yml` file.

#### Added

**New Docker files:**
- `Dockerfile` — Multi-stage build configuration for Spring Boot application
  - Stage 1: JDK image with Maven, compiles application
  - Stage 2: Lightweight JRE image, runs compiled JAR
  - Result: Small, optimized image (~500MB)

- `docker-compose.yml` — Complete orchestration configuration
  - `ollama` service (latest image) — Runs `llama3.2` and `nomic-embed-text` models
  - `chromadb` service (v1.5.3) — Vector database for embeddings
  - `docsense` service (built from Dockerfile) — Spring Boot application
  - Service dependencies ensure correct startup order
  - Internal Docker network for service-to-service communication
  - Volumes for persistent data (models, vectors, documents)

- `docker-start.sh` — Automated startup script
  - Builds and starts all containers in one command
  - Waits for services to stabilize (60 seconds)
  - Pulls required Ollama models on first run
  - Displays access URLs and next steps

- `docker-stop.sh` — Graceful shutdown script
  - Stops and removes containers
  - Preserves volumes (data survives restarts)
  - Shows additional cleanup options

- `.dockerignore` — Optimizes build context
  - Excludes unnecessary files from Docker build
  - Reduces build time and image size

#### Changed

**application.yaml:**
- Environment variables now override hardcoded localhost URLs
- `SPRING_AI_OLLAMA_BASE_URL` defaults to `http://ollama:11434` (was `http://localhost:11434`)
- `SPRING_AI_VECTORSTORE_CHROMA_CLIENT_HOST` defaults to `http://chromadb` (was `http://localhost`)
- Maintains backward compatibility for local development (falls back to localhost if env vars not set)

**README.md:**
- Added "Quick Start — Docker" section (now primary setup path)
- Moved local development setup to secondary "Local Development Setup" section
- Documented Docker files and volumes
- Simplified setup from 5 steps to 1 command

**DEVELOPER_GUIDE.md:**
- Added "Quick Start — Docker" at top of table of contents
- Added new "Docker Architecture" section (comprehensive explanation)
  - Network diagram
  - Service descriptions
  - Dockerfile strategy
  - Environment variables
  - Volume management
  - Startup flow
  - Debugging commands

#### Fixed

**ChromaDB connectivity:**
- Fixed `invalid URI scheme chromadb` error by adding `http://` prefix to ChromaDB host in docker-compose environment variables
- Spring AI's ChromaApi now correctly receives `http://chromadb:8000` instead of just `chromadb:8000`

**Maven wrapper in Docker:**
- Added `chmod +x mvnw` in Dockerfile RUN command to ensure maven wrapper is executable in container

**Docker context size:**
- Removed `.mvn` from `.dockerignore` (was preventing Maven wrapper files from being copied)
- This ensures the Docker build has access to Maven wrapper configuration

### Benefits

**For users:**
- ✅ One command to start everything: `./docker-start.sh`
- ✅ No need to manually install Ollama, Java 21, or configure PATH
- ✅ Consistent environment across different machines
- ✅ Data persists across restarts (models, vectors, documents)
- ✅ Easy cleanup: `./docker-stop.sh`

**For developers:**
- ✅ Reproducible environment for debugging and development
- ✅ Clear dependency flow (Ollama → ChromaDB → DocSense)
- ✅ Internal service discovery via Docker DNS
- ✅ Easy to extend with additional services

### Technical Details

**Startup flow:**
1. User runs `./docker-start.sh`
2. Docker Compose builds DocSense image (if needed)
3. Three containers start in order (Ollama → ChromaDB → DocSense)
4. Script waits 60 seconds for services to initialize
5. Ollama models are pulled and cached
6. Application is ready at `http://localhost:8085`

**Data persistence:**
- `ollama_data` volume: Caches downloaded models (~7-10 GB)
- `chroma_data` volume: Persists vector database state
- `./storage`: Host-mounted directory for uploaded PDFs and registry
- All survive `docker-compose down`

**Performance:**
- First startup: 5-15 minutes (initial model download)
- Subsequent startups: 30-60 seconds (cached models, all three services)
- Ollama lazy-loads models on first query (may add 5-10 seconds to first request)

### Migration from Local Setup

**If upgrading from local development setup:**

1. Stop local services:
   ```bash
   # Stop local Ollama and ChromaDB
   docker-compose down  # stops ChromaDB
   # Kill local Ollama process
   ```

2. Start fresh with Docker:
   ```bash
   ./docker-start.sh
   ```

3. Data is preserved:
   - Local `storage/` directory is mounted in container
   - Existing PDFs and document registry are accessible

### Known Limitations

- Docker Desktop or Docker Engine must be installed and running
- First startup requires 15-20 GB free disk space (for Ollama models)
- Requires `docker-compose` (usually included with Docker Desktop)
- MacOS with Apple Silicon: Ollama runs via Docker (runs on Linux VM, may be slightly slower than native)

### Documentation

- See [README.md](README.md) for quick start
- See [DEVELOPER_GUIDE.md](DEVELOPER_GUIDE.md) for architecture details
- See [HELP.md](HELP.md) for troubleshooting and usage

---

## [0.0.1] - Previous Release

Local development setup only. See commit history for details.
