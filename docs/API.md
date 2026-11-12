# API Reference

## Health Check (Phase 1)

### `GET /actuator/health`
Public endpoint returning application and PostgreSQL health status.

---

## Authentication (Phase 2)

### `POST /api/auth/register`
Registers a new user account and returns a JWT access token.

**Request (`application/json`):**
```json
{
  "name": "Alice Dev",
  "email": "alice@devdocs.ai",
  "password": "StrongPass123"
}
```

**Response (`201 Created`):**
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "tokenType": "Bearer",
  "expiresInMs": 86400000,
  "user": {
    "id": "uuid",
    "name": "Alice Dev",
    "email": "alice@devdocs.ai",
    "createdAt": "2026-11-12T11:30:00Z"
  }
}
```

**Errors:** `400 Bad Request` (validation errors with `fieldErrors`), `409 Conflict` (duplicate email).

---

### `POST /api/auth/login`
Authenticates an existing user and returns a JWT access token.

**Request (`application/json`):**
```json
{
  "email": "alice@devdocs.ai",
  "password": "StrongPass123"
}
```

**Response (`200 OK`):** Same shape as `/api/auth/register`.  
**Errors:** `401 Unauthorized` (invalid email or password).

---

### `GET /api/auth/me` *(Protected)*
Requires `Authorization: Bearer <token>`. Returns the authenticated user's profile.

**Response (`200 OK`):**
```json
{
  "id": "uuid",
  "name": "Alice Dev",
  "email": "alice@devdocs.ai",
  "createdAt": "2026-11-12T11:30:00Z"
}
```

---

## Documents (Phase 3) *(All endpoints require `Authorization: Bearer <token>`)*

### `POST /api/documents`
Uploads and extracts text from a `PDF`, `DOCX`, or `TXT` file (`multipart/form-data` with `file` and optional `title`).

**Response (`201 Created`):**
```json
{
  "id": "uuid",
  "title": "Spring Pool Notes",
  "originalFileName": "spring-notes.txt",
  "fileType": "TXT",
  "fileSize": 1024,
  "processingStatus": "COMPLETED",
  "pageCount": 1,
  "chunkCount": 2,
  "errorMessage": null,
  "createdAt": "2026-11-12T12:00:00Z",
  "updatedAt": "2026-11-12T12:00:00Z"
}
```

### `GET /api/documents`
Returns all documents belonging to the authenticated user (newest first).

### `GET /api/documents/{id}`
Returns metadata for a single document owned by the authenticated user (`404 Not Found` if missing or owned by another user).

### `DELETE /api/documents/{id}`
Deletes a document owned by the authenticated user (`204 No Content`).

---

## AI Question Answering / RAG (Phase 5) *(Protected)*

### `POST /api/chat/ask`
Embeds the user's question, searches only the authenticated user's chunks in `vector_store`, and generates a grounded Gemini answer with source references.

**Request (`application/json`):**
```json
{
  "question": "How does Spring Boot configure HikariCP?"
}
```

**Response (`200 OK`):**
```json
{
  "question": "How does Spring Boot configure HikariCP?",
  "answer": "Based on your documentation [Source 1], Spring Boot uses HikariCP...",
  "grounded": true,
  "sources": [
    {
      "documentId": "uuid",
      "documentName": "HikariCP Pooling Guide",
      "pageNumber": 1,
      "chunkIndex": 0,
      "score": 0.89,
      "excerpt": "Spring Boot configures HikariCP connection pooling..."
    }
  ]
}
```

---

## Chat Sessions & History (Phase 6) *(All endpoints require `Authorization: Bearer <token>`)*

### `POST /api/chat/sessions`
Creates a new chat session for the authenticated user. Body is optional (`{ "title": "..." }`); defaults to `"New Chat"`.

**Response (`201 Created`):**
```json
{
  "id": "uuid",
  "title": "New Chat",
  "messageCount": 0,
  "createdAt": "2026-11-12T15:00:00Z",
  "updatedAt": "2026-11-12T15:00:00Z"
}
```

### `GET /api/chat/sessions`
Lists all chat sessions owned by the authenticated user ordered by `updatedAt DESC`.

### `GET /api/chat/sessions/{id}/messages`
Returns ordered messages (`USER` and `ASSISTANT` with persisted `sources`) for an owned session (`404 Not Found` if missing or owned by another user).

### `POST /api/chat/sessions/{id}/messages`
Sends a question inside an owned session, passes up to the last 6 prior turns to `RagService`, persists both `USER` and `ASSISTANT` messages (including citations), auto-titles `"New Chat"` sessions from the first question, and returns `{ session, userMessage, assistantMessage }` (`201 Created`).

### `DELETE /api/chat/sessions/{id}`
Deletes an owned chat session and cascades deletion of its messages (`204 No Content`).


