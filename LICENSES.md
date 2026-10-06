# Licenses

Haven's own code is MIT-licensed (see `LICENSE`). This file lists every
external component and every knowledge-base source.

## Backend (Python)

| Component | Purpose | License |
| --- | --- | --- |
| FastAPI | HTTP API framework | MIT |
| Uvicorn | ASGI server | BSD-3-Clause |
| SQLAlchemy | ORM / database access | MIT |
| Alembic | Database migrations | MIT |
| psycopg 3 | PostgreSQL driver | LGPL-3.0 (used unmodified as a library) |
| pgvector-python | pgvector types for SQLAlchemy | MIT |
| Pydantic, pydantic-settings | Validation and configuration | MIT |
| PyJWT (+ cryptography) | Verifying Supabase access tokens | MIT (cryptography: Apache-2.0 / BSD) |
| httpx | HTTP client for OpenAI and Supabase APIs | BSD-3-Clause |
| pytest | Tests | MIT |

## Database

| Component | Purpose | License |
| --- | --- | --- |
| PostgreSQL | Relational database | PostgreSQL License |
| pgvector | Vector similarity search | PostgreSQL License |

## Android

| Component | Purpose | License |
| --- | --- | --- |
| Kotlin, kotlinx.coroutines, kotlinx.serialization | Language and libraries | Apache-2.0 |
| AndroidX (Core, Activity, Lifecycle, Security Crypto) | Platform support | Apache-2.0 |
| Jetpack Compose (UI, Material 3, Material Icons) | User interface | Apache-2.0 |
| OkHttp | HTTP client | Apache-2.0 |
| stream-webrtc-android (GetStream build of WebRTC) | Real-time audio connection | Apache-2.0 (WebRTC itself: BSD-3-Clause) |
| JUnit 4 | Unit tests | EPL-1.0 |

## External services (not open source; used via API)

| Service | Purpose | Terms |
| --- | --- | --- |
| OpenAI API (Realtime, transcription, text, embeddings, moderation) | Voice conversation, summaries, embeddings, safety signal | Commercial API, usage-based pricing; OpenAI Terms of Use and usage policies |
| Supabase | Hosted PostgreSQL + pgvector and Auth | Commercial platform with a free tier; core components open source (Apache-2.0 / MIT / PostgreSQL) |
| Render (or any Docker host) | Hosting the backend | Commercial platform with a free tier |

Crisis-line information is factual public information from each service's
own website.

## Knowledge-base sources

Every document in `knowledge/sources` must be listed here. Only material we
have the right to use is included; copyrighted books are **not** included.

| File | Title | Author | License | Source | Redistribution |
| --- | --- | --- | --- | --- | --- |
| `act-defusion-and-values.md` | Acceptance and commitment ideas (ACT) | Haven project contributors (original writing) | CC BY 4.0 | This repository | Yes |
| `behavioural-activation.md` | Behavioural activation | Haven project contributors (original writing) | CC BY 4.0 | This repository | Yes |
| `cbt-thinking-patterns.md` | Thinking patterns (CBT) | Haven project contributors (original writing) | CC BY 4.0 | This repository | Yes |
| `grounding-and-breathing.md` | Grounding and breathing for anxiety and panic | Haven project contributors (original writing) | CC BY 4.0 | This repository | Yes |
| `loneliness-and-connection.md` | Loneliness and connection | Haven project contributors (original writing) | CC BY 4.0 | This repository | Yes |
| `motivational-interviewing.md` | Motivational interviewing ideas | Haven project contributors (original writing) | CC BY 4.0 | This repository | Yes |
| `self-compassion.md` | Self-compassion | Haven project contributors (original writing) | CC BY 4.0 | This repository | Yes |
| `sleep-habits.md` | Sleep habits | Haven project contributors (original writing) | CC BY 4.0 | This repository | Yes |
| `solution-focused-questions.md` | Solution-focused questions | Haven project contributors (original writing) | CC BY 4.0 | This repository | Yes |
| `supportive-listening.md` | Supportive listening | Haven project contributors (original writing) | CC BY 4.0 | This repository | Yes |
| `when-to-seek-professional-help.md` | When to seek professional help | Haven project contributors (original writing) | CC BY 4.0 | This repository | Yes |
| `work-stress-and-impostor-feelings.md` | Work stress and impostor feelings | Haven project contributors (original writing) | CC BY 4.0 | This repository | Yes |

These are original plain-language summaries of widely taught, evidence-based
ideas (CBT, ACT, behavioural activation, motivational interviewing,
solution-focused approaches, mindfulness and self-compassion). They do not
reproduce text from any book.

### Adding sources

Add only public-domain, openly licensed (e.g. CC BY, CC BY-SA, CC0) or
explicitly permitted material, record its licence in the file's front matter
and in this table, and keep anything that may not be redistributed out of
this public repository. See `knowledge/README.md`.
