# Haven — a voice-first AI wellbeing companion (Android)

Haven is a personal, non-profit Android app for moments when you want to talk
something through. You open it, it greets you by name, and you talk. It
listens, responds calmly, remembers what matters (if you let it), and steers
toward real human help when things are serious.

> **Haven is an AI wellbeing and self-reflection companion, not a licensed
> therapist or medical professional. It cannot diagnose or treat mental-health
> conditions. If you are in immediate danger or may hurt yourself or someone
> else, contact local emergency services or a qualified crisis service.**

```
Open app → "Good evening, Harshit. How are you feeling?" → you talk → it listens → it replies
```

No chat screen, no typing box, no scrolling history. Three screens: onboarding,
the voice orb, and settings/privacy.

## Architecture

```
 ANDROID APP (Kotlin + Jetpack Compose)                 BACKEND (Python FastAPI)            
 ┌──────────────────────────────────────┐              ┌──────────────────────────────────┐
 │ Voice orb UI                          │  1. mint     │ Auth: verifies Supabase JWT       │
 │ VoiceSessionController ───────────────┼─────────────▶│ /v1/realtime/session              │
 │   RealtimeCoordinator (pure state     │  ephemeral   │   builds instructions: persona,   │
 │   machine: phases, tools, safety)     │  key (2 min) │   profile, memories, summaries    │
 │ WebRtcVoiceLink ──────┐               │              │   mints OpenAI client secret ─────┼──▶ OpenAI
 └───────────────────────┼───────────────┘              │                                   │
              2. mic audio + events (WebRTC)            │ /v1/conversations/{id}/tools/*    │
                         ▼                              │   memories, profile, RAG library  │
                 OpenAI Realtime API                    │ /v1/conversations/{id}/turns      │
            (speech-to-speech, VAD, interruptions)      │   safety engine (rules+moderation)│
                         │  tool calls / transcripts    │ /v1/conversations/{id}/end        │
                         └── relayed by the app ───────▶│   summary + memory extraction     │
                                                        └───────────────┬──────────────────┘
                                                                        ▼
                                                PostgreSQL + pgvector (Supabase)
                                    users · profiles · settings · conversations · summaries
                                    memories · memory_embeddings · safety_events · knowledge
```

Key decisions:

- **Speech-to-speech, not STT → LLM → TTS.** The app streams audio to the
  OpenAI Realtime API over WebRTC. Server-side semantic VAD detects when you've
  finished speaking; talking over the companion interrupts it.
- **The OpenAI API key never leaves the server.** The backend mints a
  short-lived client secret for each conversation. Tools (memory, knowledge)
  execute on the backend under your account's authorization.
- **Memory is a controlled layer, not "the model remembers".** At the end of a
  conversation the transcript is sent once to the backend, which writes a
  compact summary and extracts a few durable facts, then discards it. Only
  relevant memories and recent summaries are put in front of the model.
- **Safety runs on every utterance.** Deterministic rules (tested against an
  evaluation set) plus an optional moderation signal classify risk as
  LOW / MODERATE / HIGH / IMMEDIATE. MODERATE adds guidance to the
  conversation; HIGH/IMMEDIATE interrupts the current reply, re-steers the
  model toward human help, and shows a crisis card with tap-to-dial numbers.
- **RAG, not fine-tuning.** Psychoeducation comes from a curated, licensed
  knowledge base (`knowledge/sources`) retrieved via pgvector, with source
  metadata kept for every chunk.

## Repository layout

| Path | What |
| --- | --- |
| `android/` | Kotlin + Jetpack Compose app (WebRTC voice, onboarding, settings) |
| `backend/` | FastAPI service, Alembic migrations, safety engine, memory, RAG |
| `knowledge/` | Licensed psychoeducation sources for RAG |
| `eval/` | Safety classification cases and conversation behaviour scenarios |
| `docs/` | Deployment, APK build, architecture notes, security review |
| `PRIVACY_POLICY.md`, `TERMS.md` | Privacy policy and terms/disclaimer |
| `LICENSES.md`, `PROJECT_COSTS.md` | Third-party licences and costs |

## Quick start

1. **Backend + database** — follow [`docs/DEPLOYMENT.md`](docs/DEPLOYMENT.md)
   (Supabase for Postgres+pgvector+Auth, Render/Fly/any Docker host for the API).
   For local development:
   ```bash
   cd backend
   python -m venv .venv && . .venv/bin/activate
   pip install -r requirements-dev.txt
   cp .env.example .env            # fill in DATABASE_URL, SUPABASE_*, OPENAI_API_KEY
   alembic upgrade head
   python -m scripts.ingest_knowledge
   uvicorn app.main:app_factory --factory --reload
   pytest -q                        # 110 tests, SQLite by default
   ```
2. **APK** — follow [`docs/BUILD_APK.md`](docs/BUILD_APK.md). Either build in
   Android Studio, or push to GitHub and download the APK artifact from the
   `Android` workflow.

## Status and limitations

- The backend is fully tested (unit + API tests on SQLite and on PostgreSQL with
  pgvector, plus a 57-case safety evaluation set).
- The Android app's pure logic (realtime event coordinator, transcript ordering,
  mic level) has JVM unit tests. The full Android build runs in GitHub Actions;
  it should be smoke-tested on a real device before relying on it.
- Crisis numbers are included for IN, US, CA, GB, IE, AU and NZ; other regions
  get a link to findahelpline.com. Verify the numbers for your region.
- The keyword safety layer is a backstop, not a guarantee. It is deliberately
  conservative and will sometimes miss or over-flag; the voice model also has
  its own safety instructions.

## License

Code: MIT (see `LICENSE`). Knowledge-base text in `knowledge/sources`: CC BY 4.0.
Third-party components: see [`LICENSES.md`](LICENSES.md).
