# Project costs

Haven is a **non-profit personal project**: free, no ads, no money made. It
has no server, so there is nothing to host. The only external dependency is
Google's Gemini API, used with **your own** API key.

Free-tier limits and prices change; check Google AI Studio for the limits on
your project and ai.google.dev/pricing for current prices.

## Components

| Component | Provider | Purpose | License | Free tier | Potential cost | Open-source alternative |
| --- | --- | --- | --- | --- | --- | --- |
| Android app | This project | Everything on the phone: UI, audio, memory, safety, knowledge search | MIT | — | **Free** | — |
| Data storage | Android (on-device, encrypted) | Memories, summaries, settings | Apache-2.0 (AndroidX) | — | **Free** | — |
| Knowledge base | This project | Psychoeducation for retrieval, bundled in the APK | CC BY 4.0 | — | **Free** | — |
| Realtime voice | **Google Gemini Live API** | Speech-to-speech conversation | Commercial API | **Yes**: rate-limited free tier, limits set per Google project. Free-tier content may be used by Google to improve its products | If you enable billing: roughly US$0.005/min of your audio and US$0.018/min of the companion's audio for current Flash Live models (indicative) | Self-hosted Whisper + an open LLM + open TTS: free and private, but far more setup and slower |
| Summaries + memory extraction | Google Gemini text model (Flash class) | One short call per conversation | Commercial API | **Yes** | Fractions of a cent per conversation on paid tier | Any open LLM |
| CI / APK builds | GitHub Actions | Tests and APK build | Commercial | Free minutes for public repos and a monthly allowance for private ones | Free for this project | Local builds in Android Studio |

## If you hit free-tier limits

- Haven tells you ("Gemini's free limit was reached") and you can try again later.
- Shorter conversations use less quota; conversations also end automatically
  after 50 minutes or when the app goes to the background.
- Enabling billing on your Google project lifts the limits and switches to
  paid-tier data terms (content not used to improve Google's products).
