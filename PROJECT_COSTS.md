# Project costs

Haven is a **non-profit personal project**: it is free, has no ads and makes
no money. That does **not** make the services it depends on free or
non-profit. OpenAI in particular is a commercial, usage-billed API.

Prices and free-tier limits change often. Figures below are indicative at the
time of writing; always check the provider's pricing page.

## Components

| Component | Provider | Purpose | License | Free tier | Potential cost | Open-source alternative |
| --- | --- | --- | --- | --- | --- | --- |
| Android app | This project | UI, voice connection | MIT | — | **Free** | — |
| Backend API | This project (FastAPI) | Auth checks, memory, RAG, safety, session minting | MIT | — | **Free** (software) | — |
| Backend hosting | Render (or Fly.io, Railway, a VPS, a home server) | Runs the API container | Commercial | Render free web service (sleeps when idle, so the first request can take up to about a minute) | Free → ~US$7/month for an always-on small instance | Any Docker host you own |
| Database + vector search | Supabase (PostgreSQL + pgvector) | Profiles, memories, summaries, knowledge | PostgreSQL / Apache-2.0 | Free project with a small database (hundreds of MB; ample for one user). Free projects pause after a period of inactivity | Free → paid plan (~US$25/month) if you outgrow it | Self-hosted PostgreSQL + pgvector |
| Authentication | Supabase Auth | Email/password login | Apache-2.0 (GoTrue) | Generous monthly-active-user allowance | **Free** for personal use | Self-hosted Supabase Auth, Keycloak |
| Realtime voice | **OpenAI Realtime API** | Speech-to-speech conversation | Commercial API | None | **Paid, per audio token.** The dominant cost: roughly cents per minute of conversation, depending on model, how much each side talks and conversation length (the context grows during a session) | Self-hosted open models (e.g. Whisper + an open LLM + open TTS); much more work and usually higher latency |
| Transcription | OpenAI (`gpt-4o-mini-transcribe`) | Transcripts for safety checks and summaries | Commercial API | None | Paid, small | Whisper (MIT) self-hosted |
| Summaries + memory extraction | OpenAI (`gpt-4.1-mini` by default) | One short call per conversation | Commercial API | None | Paid, fractions of a cent per conversation | Any open LLM via an OpenAI-compatible server |
| Embeddings | OpenAI (`text-embedding-3-small`) | Memory and knowledge search | Commercial API | None | Paid, negligible at personal scale | sentence-transformers models (Apache-2.0) |
| Moderation | OpenAI moderation endpoint | Extra safety signal | Commercial API | Free to use for API customers at the time of writing | Free | Rules-only (already the primary layer) |
| Knowledge base | This project | Psychoeducation for RAG | CC BY 4.0 | — | **Free** | — |
| CI / APK builds | GitHub Actions | Tests and APK build | Commercial | Free minutes for public repos and a monthly allowance for private ones | Free for this project's usage | Local builds in Android Studio |

## Keeping costs down

- **Voice minutes are the main cost.** Set an OpenAI monthly budget and usage
  alerts in the OpenAI dashboard.
- `REALTIME_SESSIONS_PER_HOUR` (default 20) caps how many conversations can be
  started per user per hour.
- Conversations end automatically after 50 minutes, when you tap "End
  conversation", or when the app goes to the background.
- `max_output_tokens` is capped per response, and the persona is instructed to
  keep replies short.
- Switch `OPENAI_REALTIME_MODEL` to a smaller/cheaper realtime model if your
  account offers one, and compare quality.
- Set `USE_MODERATION=false` to skip the moderation call (rules still apply).
