# Learning Notes

## Phase 1 Concepts
- **Why `pgvector` inside PostgreSQL?** One database handles both relational data and 768-dim vector similarity search using HNSW indexes.
- **Why `gemini-embedding-001` at 768 dimensions?** `text-embedding-004` was retired in 2026; 768 dimensions stays under `pgvector`'s 2,000-dimension HNSW index limit.
- **Why Maven Wrapper (`mvnw` / `mvnw.cmd`)?** Guarantees reproducible builds without requiring a global Maven install.

## Phase 2 Concepts (Authentication)
- **`@MappedSuperclass` (`BaseEntity`)**: Shares `id`, `createdAt`, and `updatedAt` columns across entities without creating a separate `base_entity` table in PostgreSQL.
- **Stateless JWT Filter**: Instead of server-side HTTP sessions, each protected request carries `Authorization: Bearer <token>`. `JwtAuthenticationFilter` verifies the HMAC-SHA256 signature and populates `SecurityContextHolder` per request.
- **`UserPrincipal` Adapter**: Keeps our `User` JPA entity clean (database-only) while exposing `id` directly via `@AuthenticationPrincipal UserPrincipal` in controllers without extra database queries.
- **BCrypt Password Hashing**: Passwords are salted and hashed via `BCryptPasswordEncoder` before saving; plaintext passwords are never logged or returned in DTOs.
- **Token Storage Tradeoff**: Storing the Bearer JWT in `localStorage` works cleanly across separate frontend/backend origins (e.g., Vercel + Render), paired with strict React output escaping (no `dangerouslySetInnerHTML`) and automatic session verification via `/api/auth/me` on startup.

## Phase 3 Concepts (Document Management & Text Extraction)
- **PDFBox `3.0.8` (`Loader.loadPDF`)**: Replaces deprecated `PDDocument.load(...)` from PDFBox 2.x. Extracting page-by-page with `PDFTextStripper.setStartPage/setEndPage` preserves exact page numbers for Phase 4/5 RAG citations.
- **Apache POI (`XWPFWordExtractor`)**: Extracts paragraphs and tables from `.docx` OOXML archives cleanly.
- **No Duplicate Text Storage**: Rather than duplicating extracted text into a `TEXT` column on `documents`, we validate extraction immediately and (in Phase 4) pass chunks directly into `pgvector`.
- **IDOR Prevention (`findByIdAndUserId`)**: Returning `404 Not Found` whenever a document ID doesn't exist *or* belongs to another user prevents attackers from probing valid document UUIDs.

## Phase 4 Concepts (Chunking, Embeddings & PGVector)
- **Why Chunking (`TokenTextSplitter`)?**: Whole PDFs are too large for embedding token limits and dilute semantic similarity. Splitting into ~500-token chunks lets vector search pinpoint the exact paragraph answering a question.
- **Metadata Filtering for Multi-Tenancy**: Every chunk in `vector_store` carries `userId`, `documentId`, `documentName`, `pageNumber`, and `chunkIndex` in JSONB `metadata`. Both similarity search and document deletion filter strictly on `userId` so one user never sees or deletes another user's vectors.
- **Failure Safety**: A document stays `PROCESSING` until `vectorStore.add(chunks)` succeeds; if Gemini or PostgreSQL fails, partial vectors are cleaned up and the row is marked `FAILED` with the error reason.

## Phase 5 Concepts (RAG & Grounded Prompting)
- **Similarity Threshold Guardrail**: Before calling Gemini, `RagService` checks `vectorStore.similaritySearch(...)` with `similarityThreshold` (e.g., `0.45`) and `filterExpression` (`userId == <current-user>`). If no chunks match, we skip the LLM call and return an honest insufficient-information reply instead of letting the LLM hallucinate.
- **Low Temperature (`0.2`)**: Keeps Gemini deterministic and faithful to the retrieved excerpts for technical documentation Q&A.
- **Inline Citations**: Numbering retrieved chunks `[Source 1]`, `[Source 2]`, etc. in the prompt lets Gemini cite sources cleanly while the backend returns structured `sources[]` metadata (`documentId`, `documentName`, `pageNumber`, `chunkIndex`, `excerpt`).

## Phase 6 Concepts (Conversation Management & Multi-Turn Context)
- **Deterministic Ordering (`sequenceNumber`)**: Saving both `USER` and `ASSISTANT` messages inside one transaction can yield identical timestamps on fast clocks; an integer `sequenceNumber` guarantees exact chronological playback.
- **Sliding History Window**: `ChatService` forwards only the last 6 messages (`3` Q&A turns) as `<conversation_history>` to `RagService`, preserving follow-up context without overflowing token limits.
- **Source Persistence (`sourcesJson`)**: Storing serialized JSON citations on assistant `ChatMessage` rows avoids extra join tables while allowing past chats to render clickable source cards identically to live answers.
- **Cascade Ownership Protection**: Every session lookup uses `findByIdAndUserId(sessionId, userId)` so another user cannot read, post to, or delete a session (`404 Not Found`).

## Phase 7 Concepts (Frontend SaaS Workspace)
- **Tailwind CSS v4 Class-Based Dark Mode**: `@custom-variant dark (&:where(.dark, .dark *));` lets `ThemeContext` toggle `.dark` on `<html>` cleanly and persist the user's preference in `localStorage`.
- **Route Architecture (`Outlet` + `ProtectedRoute`)**: Wrapping `/`, `/documents`, `/chat/:sessionId`, and `/settings` inside `AppLayout` keeps the navigation sidebar mounted across route changes while `ProtectedRoute` guards every page behind JWT verification.
- **Optimistic Chat UX**: `ChatPage` appends the user prompt immediately while Gemini responds (and auto-creates a session on the first message if none exists), rolling back cleanly with a toast notification if the request fails.
