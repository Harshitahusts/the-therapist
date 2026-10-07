# Licenses

Haven's own code is MIT-licensed (see `LICENSE`). This file lists every
external component and every knowledge-base source.

## Android app

| Component | Purpose | License |
| --- | --- | --- |
| Kotlin, kotlinx.coroutines, kotlinx.serialization | Language and libraries | Apache-2.0 |
| AndroidX (Core, Activity, Lifecycle, Security Crypto) | Platform support, encrypted storage | Apache-2.0 |
| Jetpack Compose (UI, Material 3, Material Icons) | User interface | Apache-2.0 |
| OkHttp (+ Okio) | HTTPS and WebSocket client for the Gemini API | Apache-2.0 |
| JUnit 4 | Unit tests | EPL-1.0 |

## External service (not open source; used via API)

| Service | Purpose | Terms |
| --- | --- | --- |
| Google Gemini API (Live API and a text model) | Voice conversation and end-of-conversation summaries | Google APIs Terms of Service and Gemini API Additional Terms. Used with the user's own API key; free tier available, with free-tier content usable by Google to improve its products |

The Live API wire format was implemented from Google's public API reference and
the open-source `google-genai` Python SDK (Apache-2.0); no SDK code is included.

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
