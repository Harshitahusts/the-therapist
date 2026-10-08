# Privacy Policy

_Last updated: 8 October 2026_

Haven is a personal, non-profit project: an AI wellbeing and self-reflection
companion for Android. It has **no server and no account**. This policy
explains what data the app handles, where it goes, and how you control it.

## Summary

- Everything Haven stores is kept **only on your phone**, encrypted.
- To hold a conversation, your **voice** and the companion's context are sent
  to **Google's Gemini API** using **your own API key**.
- **On Gemini's free tier, Google may use what you send to improve its
  products, and human reviewers may read it.** Avoid sharing details you
  wouldn't want reviewed.
- Haven does **not** record audio and does **not** store full transcripts.
- You can **view, delete and export** everything at any time.

## What is stored on your phone

| Data | Why |
| --- | --- |
| Your Gemini API key and the chosen models | To connect to Gemini |
| Profile: name, preferred name, timezone, language | Greeting you, time-of-day awareness |
| Settings: memory on/off, crisis-support region, auto-start | Respecting your choices; showing the right crisis numbers |
| Conversation summaries (memory on) | Context for future conversations |
| Long-term memories (memory on) | Remembering goals, projects, preferences, important events |
| Wellbeing check-ins: the questions you answered, your answers and your Well-Being Count | Showing your count; giving the companion gentle context |
| Conversation start/end times | Your export; basic bookkeeping |
| Safety events: risk level and category only | Never the words you said |

All of it is stored with Android's EncryptedSharedPreferences, using a key
held in the Android Keystore, and is excluded from cloud backup and device
transfer. Passwords, card and account numbers, API keys and ID numbers are
filtered out of anything Haven remembers.

## What is sent to Google (Gemini API)

| When | What |
| --- | --- |
| During a conversation | Your microphone audio; the companion's instructions, which include your preferred name, local time, relevant memories, recent conversation summaries and your latest check-in (if from the past week); results of the companion's tool calls (e.g. a memory or a knowledge-base passage) |
| At the end of a conversation (memory on) | The conversation transcript, once, to write the summary and extract memories |
| When you add or change your key | A request listing the models your key can use |

Google processes this under the Gemini API Additional Terms and Google's
privacy policy. **For unpaid (free-tier) use, Google states that it may use
submitted content to provide and improve its products, and that human
reviewers may read, annotate and process it.** If you enable billing on your
Google project, paid-tier terms apply instead. See
ai.google.dev/gemini-api/terms.

No data is sent to the Haven project or anyone else. The app contains no
analytics or advertising SDKs.

## Retention

| Data | Retention |
| --- | --- |
| Audio | Not stored by Haven |
| Full transcripts | Not stored by Haven (held in memory during the conversation, then discarded) |
| Summaries and memories | Until you delete them or delete everything |
| Everything on the phone | Until you delete it in Settings or uninstall the app |

Data sent to Google follows Google's retention policies.

## Your controls

In **Settings**:

- **Remember what I tell you?** — memory on or off.
- **What I remember** — see every memory; delete any one.
- **Forget everything about me** — deletes all memories, summaries and check-ins.
- **Export my data** — save everything Haven stores as a JSON file.
- **Delete everything** — deletes all data, your key and settings.

In conversation you can also say "forget that". Uninstalling the app deletes
all of its data.

## Security

Traffic to Google uses TLS. The API key is stored encrypted and sent only to
Google. Don't share an APK or device backup containing your key.

## Children

Haven is intended for adults (18+).

## Not for emergencies

Haven is not a crisis service. If you are in danger, contact local emergency
services or a crisis line.

## Changes

Material changes to this policy will be noted in this file with a new date.

## Contact

Open an issue on the project's GitHub repository.
