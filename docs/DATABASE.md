# Database Design

## Base Entity (`BaseEntity`)
All JPA entities extend `com.devdocs.entity.BaseEntity` (`@MappedSuperclass`), which provides:
- `id` (`UUID`, primary key, generated via `GenerationType.UUID`)
- `created_at` (`TIMESTAMPTZ`, automatically populated on insert via `@CreationTimestamp`)
- `updated_at` (`TIMESTAMPTZ`, automatically updated on save via `@UpdateTimestamp`)

## Implemented Tables

### `users` (Phase 2)
| Column | Type | Constraints |
| :--- | :--- | :--- |
| `id` | `UUID` | Primary Key |
| `name` | `VARCHAR(100)` | `NOT NULL` |
| `email` | `VARCHAR(255)` | `NOT NULL`, `UNIQUE` (stored trimmed & lowercase) |
| `password` | `VARCHAR(255)` | `NOT NULL` (BCrypt hash) |
| `created_at` | `TIMESTAMPTZ` | `NOT NULL` |
| `updated_at` | `TIMESTAMPTZ` | `NOT NULL` |

### `documents` (Phase 3)
| Column | Type | Constraints |
| :--- | :--- | :--- |
| `id` | `UUID` | Primary Key |
| `title` | `VARCHAR(255)` | `NOT NULL` |
| `original_file_name` | `VARCHAR(255)` | `NOT NULL` |
| `file_type` | `VARCHAR(20)` | `NOT NULL` (`PDF`, `DOCX`, `TXT`) |
| `file_size` | `BIGINT` | `NOT NULL` |
| `processing_status` | `VARCHAR(20)` | `NOT NULL` (`PENDING`, `PROCESSING`, `COMPLETED`, `FAILED`) |
| `page_count` | `INTEGER` | Nullable |
| `chunk_count` | `INTEGER` | Nullable |
| `error_message` | `TEXT` | Nullable |
| `user_id` | `UUID` | `NOT NULL`, FK → `users.id` |
| `created_at` | `TIMESTAMPTZ` | `NOT NULL` |
| `updated_at` | `TIMESTAMPTZ` | `NOT NULL` |

### `vector_store` (Phase 4 — Managed by Spring AI `PgVectorStore`)
| Column | Type | Purpose |
| :--- | :--- | :--- |
| `id` | `UUID` | Primary Key |
| `content` | `TEXT` | Extracted & split document chunk text |
| `metadata` | `JSONB` | `{ "documentId", "userId", "documentName", "pageNumber", "chunkIndex" }` |
| `embedding` | `vector(768)` | 768-dim vector from `gemini-embedding-001` (HNSW cosine index) |

### `chat_sessions` (Phase 6)
| Column | Type | Constraints |
| :--- | :--- | :--- |
| `id` | `UUID` | Primary Key |
| `title` | `VARCHAR(255)` | `NOT NULL` (defaults to `"New Chat"`, auto-titled on first message) |
| `message_count` | `INTEGER` | `NOT NULL` (tracks total messages; updates `updated_at` cleanly) |
| `user_id` | `UUID` | `NOT NULL`, FK → `users.id` |
| `created_at` | `TIMESTAMPTZ` | `NOT NULL` |
| `updated_at` | `TIMESTAMPTZ` | `NOT NULL` |

### `chat_messages` (Phase 6)
| Column | Type | Constraints |
| :--- | :--- | :--- |
| `id` | `UUID` | Primary Key |
| `content` | `TEXT` | `NOT NULL` |
| `role` | `VARCHAR(20)` | `NOT NULL` (`USER`, `ASSISTANT`) |
| `sequence_number` | `INTEGER` | `NOT NULL` (deterministic ordering within a session) |
| `grounded` | `BOOLEAN` | Nullable (`null` for `USER`, `true`/`false` for `ASSISTANT`) |
| `sources_json` | `TEXT` | Nullable (JSON array of persisted `SourceReferenceResponse` items) |
| `session_id` | `UUID` | `NOT NULL`, FK → `chat_sessions.id` (cascade deleted with session) |
| `created_at` | `TIMESTAMPTZ` | `NOT NULL` |
| `updated_at` | `TIMESTAMPTZ` | `NOT NULL` |
