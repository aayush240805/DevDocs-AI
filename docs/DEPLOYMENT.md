# Production Deployment Guide

DevDocs AI consists of three components:
1. **Database:** Managed PostgreSQL 16+ with `pgvector` enabled (e.g., Neon, Supabase, or Render Postgres).
2. **Backend:** Spring Boot containerized with `backend/Dockerfile` (e.g., Render Web Service or Railway).
3. **Frontend:** React + Vite static SPA (e.g., Vercel).

---

## 1. PostgreSQL + pgvector Setup
Provision a PostgreSQL database and enable the `vector` extension once as a database superuser/admin:
```sql
CREATE EXTENSION IF NOT EXISTS vector;
```
Spring Boot (`ddl-auto: update` + Spring AI `initialize-schema: true`) automatically creates `users`, `documents`, `chat_sessions`, `chat_messages`, and `vector_store` (768-dim HNSW cosine index) on first startup.

---

## 2. Backend Deployment (Docker / Render)
- **Root Directory / Context:** `backend`
- **Dockerfile:** `backend/Dockerfile`
- **Health Check Path:** `/actuator/health`
- **Required Environment Variables:**

| Variable | Example / Description |
| :--- | :--- |
| `SPRING_PROFILES_ACTIVE` | `prod` |
| `DB_URL` | `jdbc:postgresql://<host>:<port>/<dbname>?sslmode=require` |
| `DB_USERNAME` | Database username |
| `DB_PASSWORD` | Database password |
| `JWT_SECRET` | Base64-encoded random 256-bit+ secret (generate with `openssl rand -base64 48`) |
| `JWT_EXPIRATION_MS` | `86400000` (24 hours) |
| `GEMINI_API_KEY` | Google AI Studio API key |
| `GEMINI_CHAT_MODEL` | `gemini-3.5-flash-lite` |
| `CORS_ALLOWED_ORIGINS` | Exact frontend URL(s), e.g. `https://devdocs-ai.vercel.app` |

---

## 3. Frontend Deployment (Vercel)
- **Root Directory:** `frontend`
- **Framework Preset:** Vite
- **Build Command:** `npm run build`
- **Output Directory:** `dist`
- **SPA Routing:** Included in `frontend/vercel.json` so refreshing nested routes (`/documents`, `/chat/:id`, `/settings`) resolves cleanly.
- **Environment Variable:**
  - `VITE_API_BASE_URL=https://<your-backend-domain>/api`

---

## 4. Production Security Checklist
- Never commit `.env` files or API keys to Git.
- Generate a fresh Base64 `JWT_SECRET` (minimum 32 random bytes / 256 bits).
- Restrict `CORS_ALLOWED_ORIGINS` strictly to your deployed HTTPS frontend origin(s).
- `application-prod.yml` sets `management.endpoint.health.show-details: when-authorized` and disables SQL logging so internal database metadata is never leaked publicly.
