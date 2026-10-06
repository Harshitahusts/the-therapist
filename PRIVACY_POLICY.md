# Privacy Policy

_Last updated: 6 October 2026_

Haven is a personal, non-profit project: an AI wellbeing and self-reflection
companion for Android. This policy explains what data the app handles, why,
and how you control it. If you self-host Haven, you are the operator of your
deployment and this policy describes how the software behaves.

## Summary

- Your **voice** is streamed to OpenAI to power the conversation. Haven does
  **not** record or store audio.
- **Full transcripts are not stored.** At the end of a conversation the
  transcript is used once to write a short summary and extract a few useful
  facts (only if memory is on), and is then discarded.
- With **memory on**, Haven stores short conversation summaries and long-term
  memories so it can remember context. With memory off, it stores neither.
- You can **view, delete and export** everything, and delete your account.

## What we collect and why

| Data | Why | Where it is stored |
| --- | --- | --- |
| Email address and login credentials | To create your account and keep your data private to you | Supabase Auth (passwords are hashed by Supabase) |
| Profile: name, preferred name, timezone, language, communication preferences | Greeting you, time-of-day awareness, speaking your language | App database |
| Settings: memory on/off, crisis-support region | Respecting your choices; showing the right crisis numbers | App database |
| Microphone audio (during a conversation only) | Real-time conversation | Streamed to OpenAI; not stored by Haven |
| Transcript (in memory, during and at the end of a conversation) | Safety checks on each thing you say; writing a summary at the end | Not stored. Sent to OpenAI for summarising and moderation, then discarded |
| Conversation summaries (memory on) | Context for future conversations | App database |
| Long-term memories (memory on) | Remembering goals, projects, preferences, important events | App database, with a numeric embedding used for search |
| Conversation start/end times and duration | Basic operation and your export | App database |
| Safety events: risk level and category only | Understanding how often safety responses are triggered | App database; the words you said are never stored |

Haven deliberately filters passwords, card and account numbers, API keys and
government ID numbers out of anything it remembers.

## Third-party processing

- **OpenAI** (Realtime API, transcription, text and embedding models,
  moderation) processes your audio and text to produce responses, summaries,
  embeddings and safety signals. OpenAI processes API data under its own
  terms and policies; see openai.com/policies. At the time of writing, OpenAI
  states that API data is not used to train its models by default.
- **Supabase** hosts authentication and the database.
- **Your backend host** (for example Render or Fly.io) runs the API server.

No data is sold, used for advertising, or shared with anyone else. The app
contains no analytics or advertising SDKs.

## On your device

Login tokens and onboarding choices are stored in Android encrypted shared
preferences (keys held in the Android Keystore). They are excluded from cloud
backup and device transfer. Nothing about your conversations is stored on the
device.

## Retention

| Data | Retention |
| --- | --- |
| Audio | Not stored |
| Full transcripts | Not stored (processed once, then discarded) |
| Summaries and memories | Until you delete them, turn off memory and clear them, or delete your account |
| Safety events, conversation times | Until you delete your data or account |
| Account | Until you delete it |

Data held by third parties (e.g. OpenAI API logs) follows their retention
policies.

## Your controls

In **Settings**:

- **Remember what I tell you?** — turn memory on or off.
- **What I remember** — see every memory; delete any one; or **Forget
  everything about me** (memories and summaries).
- **Export my data** — download everything stored about you as JSON.
- **Delete all my data** — deletes your profile, settings, memories,
  summaries, conversation history and safety events, keeping your login.
- **Delete my account** — deletes all of the above and your login.

In conversation you can also say "forget that".

## Security

All traffic is encrypted in transit (HTTPS / DTLS-SRTP for voice). The OpenAI
API key is held only on the server; the app receives a single-use key that
expires within minutes. Every database query is scoped to the signed-in user.

## Children

Haven is intended for adults (18+).

## Not for emergencies

Haven is not a crisis service. If you are in danger, contact local emergency
services or a crisis line.

## Changes

Material changes to this policy will be noted in this file with a new date.

## Contact

Open an issue on the project's GitHub repository.
