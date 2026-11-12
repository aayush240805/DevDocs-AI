# DevDocs AI

AI-powered technical documentation assistant built with **Spring Boot 3.5**, **Spring AI**, **Google Gemini**, **PostgreSQL + pgvector**, and **React + Vite + Tailwind CSS**.

## Features (Planned & Incremental)
- Secure user registration and JWT authentication
- Upload technical documents (`PDF`, `DOCX`, `TXT`) with validation and text extraction
- Automatic chunking, Gemini embeddings, and `pgvector` similarity storage
- Retrieval-Augmented Generation (RAG) with source citations and user-level data isolation
- Persistent chat history per user

## Technology Stack
- **Backend:** Java 21, Spring Boot 3.5.16, Spring Web, Spring Data JPA, Spring Security, Spring AI 1.1.x, Lombok
- **Database:** PostgreSQL 16 with `pgvector`
- **AI:** Google Gemini (`gemini-3.5-flash-lite` for chat, `gemini-embedding-001` at 768 dimensions for embeddings)
- **Frontend:** React 19, Vite, Tailwind CSS v4, React Router, Axios, Lucide React
- **Infrastructure:** Docker & Docker Compose

## Environment Configuration
1. Copy `.env.example` to `.env` in the project root:
   ```bash
   cp .env.example .env
   ```
2. Fill in your `DB_*`, `JWT_SECRET`, and (starting in Phase 4) `GEMINI_API_KEY` values in `.env`. Never commit `.env`.

## Run Instructions (Local Development)

### 1. Start PostgreSQL with pgvector
```bash
docker compose up -d
```

### 2. Start Backend (Spring Boot)
```bash
cd backend
./mvnw spring-boot:run
```
*(On Windows PowerShell, use `.\mvnw.cmd spring-boot:run`.)*

### 3. Start Frontend (React + Vite)
```bash
cd frontend
npm install
npm run dev
```
Open `http://localhost:5173`.

## API Overview
- `GET /actuator/health` — Backend and PostgreSQL connectivity status (Phase 1)
- `POST /api/auth/register` — Register a new user and receive a JWT (Phase 2)
- `POST /api/auth/login` — Log in and receive a JWT (Phase 2)
- `GET /api/auth/me` — Fetch the authenticated user's profile (Phase 2, protected)
- `POST /api/documents` — Upload and extract a `PDF`, `DOCX`, or `TXT` document (Phase 3, protected)
- `GET /api/documents` — List authenticated user's uploaded documents (Phase 3, protected)
- `GET /api/documents/{id}` — Fetch metadata for a single owned document (Phase 3, protected)
- `DELETE /api/documents/{id}` — Delete an owned document and its vectors (Phase 3/4, protected)
- `POST /api/chat/ask` — Ask a question grounded in your uploaded documents with source citations (Phase 5, protected)
- `POST /api/chat/sessions` — Create a new persistent chat session (Phase 6, protected)
- `GET /api/chat/sessions` — List authenticated user's chat sessions (Phase 6, protected)
- `GET /api/chat/sessions/{id}/messages` — Retrieve ordered chat messages and citations for an owned session (Phase 6, protected)
- `POST /api/chat/sessions/{id}/messages` — Send a question in a chat session with conversation history and persist the Q&A exchange (Phase 6, protected)
- `DELETE /api/chat/sessions/{id}` — Delete an owned chat session and its messages (Phase 6, protected; see [docs/API.md](docs/API.md).)

## Deployment
Production containerization (`backend/Dockerfile`, `application-prod.yml`), Vercel SPA routing (`frontend/vercel.json`), and cloud deployment instructions are documented in [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md).
