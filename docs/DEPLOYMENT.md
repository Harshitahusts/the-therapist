# Deploying the backend

The backend is a single Docker container. The cheapest setup that works well
for one person:

- **Supabase** (free): PostgreSQL with pgvector, and Auth.
- **Render** (free web service) or any Docker host: the API.
- **OpenAI** (paid, usage-based): voice, summaries, embeddings.

## 1. Supabase

1. Create a project at supabase.com.
2. **Authentication → Providers → Email**: make sure Email is enabled. For a
   personal app you may turn **Confirm email** off so sign-up logs you in
   immediately; if you leave it on, the app asks you to confirm then sign in.
3. Collect these values (Project Settings):
   - **Project URL** → `SUPABASE_URL` (backend) and `HAVEN_SUPABASE_URL` (app).
   - **anon / publishable key** → `HAVEN_SUPABASE_ANON_KEY` (app only; it is
     designed to be public).
   - **JWT secret** (legacy HS256, under JWT Keys) → `SUPABASE_JWT_SECRET`.
     If your project uses the newer asymmetric signing keys, leave this empty:
     the backend then verifies tokens against the project's JWKS endpoint.
   - **service_role / secret key** → `SUPABASE_SERVICE_ROLE_KEY` (backend only;
     used solely to delete your login when you delete your account). Never put
     it in the app.
   - **Database connection string**: Connect → *Session pooler* (works over
     IPv4, which most free hosts need) → `DATABASE_URL`.

The first migration enables the `vector` extension, creates all tables and
HNSW indexes, and enables row-level security on every table so nothing is
reachable through Supabase's auto-generated public API. The backend connects
as the table owner and enforces per-user access itself.

## 2. OpenAI

Create an API key at platform.openai.com → `OPENAI_API_KEY`. **Set a monthly
budget and usage alerts.** Voice minutes are the main cost (see
`PROJECT_COSTS.md`).

## 3. Deploy the API

### Render (blueprint)

1. Push this repository to GitHub.
2. In Render: **New → Blueprint**, select the repo. `render.yaml` defines the
   service.
3. Fill in the environment variables it asks for: `DATABASE_URL`,
   `SUPABASE_URL`, `SUPABASE_JWT_SECRET`, `SUPABASE_SERVICE_ROLE_KEY`,
   `OPENAI_API_KEY`.
4. Deploy. The container runs `alembic upgrade head` on start, then serves on
   `$PORT`. Check `https://<your-service>.onrender.com/health` returns
   `{"ok": true}`.

The free instance sleeps after inactivity; the first conversation after a
while may take up to about a minute to connect. A paid instance avoids this.

### Any other Docker host

```bash
docker build -f backend/Dockerfile -t haven-backend .
docker run -p 8000:8000 --env-file backend/.env haven-backend
```

Put it behind HTTPS (release builds of the app refuse plain HTTP).

## 4. Load the knowledge base

Run once (and again whenever `knowledge/sources` changes), from any machine
that can reach the database:

```bash
cd backend
pip install -r requirements.txt
DATABASE_URL=... OPENAI_API_KEY=... python -m scripts.ingest_knowledge
# {"documents": 12, "skipped": 0, "chunks": ...}
```

## 5. Point the app at it

Set `HAVEN_BACKEND_URL=https://<your-service>` when building the APK (see
`BUILD_APK.md`).

## Local development

```bash
docker run -d --name haven-db -e POSTGRES_PASSWORD=pg -p 5432:5432 pgvector/pgvector:pg16
cd backend
python -m venv .venv && . .venv/bin/activate
pip install -r requirements-dev.txt
cp .env.example .env   # DATABASE_URL=postgresql://postgres:pg@localhost:5432/postgres
alembic upgrade head
python -m scripts.ingest_knowledge
uvicorn app.main:app_factory --factory --reload --host 0.0.0.0
```

`AI_PROVIDER=offline` runs everything except voice without an OpenAI key
(deterministic local embeddings and a trivial summariser), which is handy for
working on the API. Don't mix offline and OpenAI embeddings in one database.

### Tests

```bash
pytest -q                                   # SQLite
TEST_DATABASE_URL=postgresql://postgres:pg@localhost:5432/postgres \
DATABASE_URL=postgresql://postgres:pg@localhost:5432/postgres pytest -q   # after alembic upgrade head
```

## Configuration reference

See `backend/.env.example`. Notable settings:

| Variable | Default | Notes |
| --- | --- | --- |
| `OPENAI_REALTIME_MODEL` | `gpt-realtime` | Any realtime model your account has |
| `OPENAI_REALTIME_VOICE` | `marin` | Realtime voice name |
| `OPENAI_TEXT_MODEL` | `gpt-4.1-mini` | Used for end-of-conversation summaries (must support JSON mode) |
| `EMBEDDING_DIM` | `1536` | Fixed at first migration; changing it needs a fresh database |
| `USE_MODERATION` | `true` | Extra safety signal on each utterance |
| `REALTIME_SESSIONS_PER_HOUR` | `20` | Per-user cost guard |
| `DEFAULT_CRISIS_REGION` | `INTL` | Used until the user picks a region |
