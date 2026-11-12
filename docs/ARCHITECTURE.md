# Architecture

## System Overview
DevDocs AI uses a monolithic, layered **Spring Boot** backend, a **React (Vite)** SPA frontend, and a single **PostgreSQL 16 + pgvector** database.

```text
React (Vite + Tailwind)  <-- HTTP/JSON + JWT -->  Spring Boot REST API
                                                     ├── PostgreSQL (JPA + pgvector)
                                                     └── Google Gemini API (Spring AI)
```

## Component Responsibilities
- **Frontend (`frontend/`)**: Handles UI, JWT storage/headers, file uploads, and chat rendering. Never communicates directly with PostgreSQL or Gemini.
- **Backend (`backend/`)**:
  - `controller/`: HTTP request/response handling and validation.
  - `service/`: Business logic (`AuthService`, `DocumentService`, `DocumentProcessingService`, `ChatService`, `RagService`).
  - `repository/`: Spring Data JPA interfaces.
  - `entity/`: JPA entities extending `BaseEntity` (`id`, `createdAt`, `updatedAt`).
  - `security/`: JWT token creation/validation and Spring Security filters.
  - `exception/`: Centralized error formatting via `GlobalExceptionHandler`.
- **Database (`docker-compose.yml`)**: Stores relational entities and 768-dimensional document chunk embeddings in `vector_store`.

## RAG Workflow
1. **Ingestion**: Upload file → validate → extract text (PDFBox / POI / UTF-8) → split with `TokenTextSplitter` → embed via `gemini-embedding-001` → store in `pgvector` with `userId` and `documentId` metadata → mark `COMPLETED`.
2. **Retrieval & Generation**: User asks question → verify session ownership → embed question → similarity search filtered by `userId` → build grounded prompt → call `gemini-3.5-flash-lite` → return answer + citations.
