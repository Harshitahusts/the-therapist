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

No chat screen, no typing box, no account, no server. Just an APK and your own
free Gemini API key.

## The journey

1. **Welcome**: a little smiling leaf buddy drifts around the screen ("Click me! 👋").
2. **Name** and **a few honest words**: not a therapist, privacy, memory on/off.
3. **Check-in**: choose 3, 5 or 10 gentle questions (Often / Sometimes / Rarely /
   Never). Every question is followed by its own small, uplifting note.
4. **Well-Being Count (WBC)**: a 0–100 score with a friendly band (Blooming,
   Growing, Sprouting, Needs gentle care). A reflection, not a diagnosis; the
   companion keeps it in mind when you talk.
5. **Voice key and microphone** (first time only).
6. **Talk**: the voice orb, a session timer, and six mixable soundscapes
   (Air, Water, Birds, River, Bonfire, Fresh) that play softly under the
   conversation and dip while Haven speaks. Haven's voice has a very light echo.

Sign-in with Google will be added before any public release.

## How it works

```
 ┌───────────────────────────── Android phone ─────────────────────────────┐
 │  Voice orb UI                                                            │
 │  VoiceSessionController                                                  │
 │    MicRecorder (16 kHz, echo cancel) ──┐        ┌── SpeakerPlayer (24 kHz)│
 │    LiveCoordinator (pure state machine: phases, interruptions,           │
 │      tool calls, transcripts, safety re-steer, session resumption)       │
 │    Safety classifier (on-device rules) → crisis card + guidance          │
 │    Tools: memories · profile · summaries · knowledge search (BM25)       │
 │    DataRepository → EncryptedSharedPreferences (Android Keystore)        │
 └────────────────────────────────┬─────────────────────────────────────────┘
                                  │ WebSocket (audio in/out, tool calls)
                                  ▼
                 Google Gemini Live API  (your own free API key)
                 + one Gemini text call per conversation for the summary
```

- **Speech-to-speech.** The phone streams microphone audio to the Gemini Live
  API and plays the reply as it arrives. Gemini detects when you've finished
  speaking; talking over the companion interrupts it.
- **Everything else runs on the phone.** Memories, summaries, the
  psychoeducation library, the safety classifier and the tools the companion
  calls all live in the app. Data is stored encrypted with a key held in the
  Android Keystore and excluded from backups.
- **Memory is a controlled layer.** At the end of a conversation the transcript
  is sent once to a Gemini text model to write a compact summary and extract a
  few durable facts, then discarded. Only relevant memories and recent
  summaries are given to the companion.
- **Safety runs on every utterance**, on the device, with deterministic rules
  tested against a 57-case evaluation set. Anything above LOW cuts off the
  current reply and re-steers the companion with guidance; HIGH/IMMEDIATE also
  shows a crisis card with tap-to-dial numbers for your region.
- **Soundscapes are synthesised on the phone** (no recordings, no downloads)
  and mixed with the companion's voice into one audio stream, so the phone's
  echo canceller keeps them out of the microphone.
- **RAG without the cloud.** Twelve original CC BY 4.0 psychoeducation
  documents (`knowledge/sources`) ship inside the APK and are searched with
  BM25 when the companion wants grounded material.

## Get it running

1. **Get a free Gemini API key**: https://aistudio.google.com/apikey → *Create API key*.
2. **Get the APK**: on GitHub, open **Actions → Android**, pick the latest
   green run, and download **haven-debug-apk** (or build it yourself; see
   [`docs/BUILD_APK.md`](docs/BUILD_APK.md)).
3. Install it on your phone, open it, follow the short intro, and paste your key.

That's it. There is nothing to deploy.

## Privacy, in one paragraph

Nothing is stored anywhere except your phone. To talk, your voice and the
companion's context (your name, relevant memories, recent summaries) are sent to
Google's Gemini API with your key. **On Gemini's free tier, Google may use that
content to improve its products and human reviewers may read it.** Haven says
so during onboarding. See [`PRIVACY_POLICY.md`](PRIVACY_POLICY.md).

## Repository layout

| Path | What |
| --- | --- |
| `android/` | The app (Kotlin + Jetpack Compose) |
| `android/app/src/main/java/app/haven/companion/core/` | Platform-free logic: live session, safety, memory, knowledge, Gemini client, data |
| `knowledge/` | Licensed psychoeducation sources, bundled into the APK |
| `eval/` | Safety classification cases (run as unit tests) and conversation rubric |
| `docs/` | APK build guide and security review |
| `PRIVACY_POLICY.md`, `TERMS.md`, `LICENSES.md`, `PROJECT_COSTS.md` | Policies, licences, costs |

## Development

```bash
cd android
./gradlew testDebugUnitTest   # core logic, safety eval, knowledge, live-session protocol
./gradlew assembleDebug       # app/build/outputs/apk/debug/app-debug.apk
```

## Status and limitations

- The core logic has 56 JVM unit tests (including all 57 safety cases). The app
  is built in CI; voice has not yet been tried on a real device, so expect
  some tuning (echo on loudspeaker in particular; headphones are best).
- Gemini's free-tier limits are set per Google project and can change; if you
  hit them, Haven says so and you can try again later.
- Crisis numbers are included for IN, US, CA, GB, IE, AU and NZ; other regions
  get findahelpline.com. Verify the numbers for your region.
- The keyword safety layer is a backstop, not a guarantee; the voice model also
  has its own safety instructions.

## License

Code: MIT (see `LICENSE`). Knowledge-base text in `knowledge/sources`: CC BY 4.0.
Third-party components: see [`LICENSES.md`](LICENSES.md).
