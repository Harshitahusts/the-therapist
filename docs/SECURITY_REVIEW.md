# Security and privacy review

Review of the on-device version (October 2026). Scope: the Android app and its
build configuration. There is no backend.

## Secrets

| Check | Result |
| --- | --- |
| API keys compiled into the APK | None. The user enters their own Gemini key at first launch. |
| Where the key is stored | EncryptedSharedPreferences (AES-256-GCM, key in Android Keystore), excluded from backup and device transfer |
| Where the key is sent | Only to `generativelanguage.googleapis.com`, in the `x-goog-api-key` header (never in URLs or logs) |
| Secrets in the repository | None. `local.properties` and keystores are git-ignored; CI signing uses repository secrets. |

## Data at rest

- Everything Haven stores (profile, settings, memories, summaries,
  conversation times, safety levels) is one JSON document in
  EncryptedSharedPreferences.
- No audio is recorded. Transcripts exist only in memory during a conversation
  and are sent once to Gemini to write the summary, then dropped.
- Safety events store level and category, never text.
- Memories pass a sensitive-data filter (passwords, API keys incl. Google
  `AIza…` keys, card/account numbers, ID numbers) whether saved by the voice
  tool, by summary extraction or by the user.
- `android:allowBackup="false"` plus data-extraction rules exclude everything
  from cloud backup and device-to-device transfer.

## Data in transit

- HTTPS/WSS only; the network security config disallows cleartext.
- Content sent to Google: audio, the instructions (preferred name, local time,
  relevant memories, recent summaries), tool results, and the end-of-conversation
  transcript. This is disclosed in onboarding and the privacy policy, including
  that free-tier content may be used by Google and read by human reviewers.

## Abuse and safety controls

- On-device safety classifier on every user turn (57-case eval in unit tests);
  MODERATE+ re-steers the model, HIGH+ shows tap-to-dial crisis resources.
- Tool calls run locally against the user's own data only; unknown tool names
  return an error.
- Conversations end after 50 minutes and whenever the app leaves the
  foreground (the microphone is never used in the background).
- Logs contain event types and error codes only, never user content.

## Known limitations / accepted risks

1. **Key extraction.** A key stored on a device can be read by someone with
   root access to that device. Acceptable for a personal app; don't share
   device backups or rooted devices.
2. **Free-tier data use.** On Gemini's free tier Google may use content to
   improve its products. Users who want stronger guarantees can enable billing
   on their Google project.
3. **Keyword safety layer** can miss novel phrasings or over-flag. It is a
   backstop alongside the model's own instructions.
4. **Echo on loudspeaker** may cause false interruptions on some devices; the
   platform echo canceller is enabled, and headphones are recommended.
5. The app has been built and unit-tested in CI but not yet exercised on a
   physical device as part of this review.
