# Security and privacy review

Review of the on-device version (October 2026). Scope: the Android app and its
build configuration. There is no backend.

## Secrets

| Check | Result |
| --- | --- |
| Gemini key | Built into the APK from the `HAVEN_GEMINI_API_KEY` repository secret (never committed), or entered by the user at first launch. |
| Key bound to this app | Every request to Google carries `X-Android-Package` and `X-Android-Cert` (SHA-1 of the signing certificate), so the key can be restricted in Google Cloud to this app. A key copied out of the APK then fails elsewhere. |
| Where a user-entered key is stored | EncryptedSharedPreferences (AES-256-GCM, key in Android Keystore), excluded from backup and device transfer |
| Where the key is sent | Only to `generativelanguage.googleapis.com`, in a header (never in URLs or logs) |
| Secrets in the repository | None. `local.properties` and keystores are git-ignored; CI signing uses repository secrets. |
| CI | Workflow token is read-only (`permissions: contents: read`); the APK artifact (which can contain the key) is kept for 7 days only. |

## Device protections

- The APK people install is the **release** build: not debuggable, so its data can't be read with USB debugging (`run-as`).
- Taps that arrive through another app's overlay are ignored (tapjacking protection).
- On Android 13+, conversations are hidden from the recent-apps preview.
- Only the launcher activity is exported; there are no services, receivers or providers.

## Data at rest

- Everything Haven stores (profile, settings, memories, summaries,
  conversation times, safety levels) is one JSON document in
  EncryptedSharedPreferences.
- No audio is recorded. Transcripts exist only in memory during a conversation
  and are sent once to Gemini to write the summary, then dropped.
- Safety events store level and category, never text.
- "Forget everything" clears memories, summaries, check-ins, conversation history and safety events;
  "Delete everything" also removes settings and the stored key.
- Storage is bounded (500 memories, 200 summaries, 1000 conversation records, 500 safety events).
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

## Setup checklist for the key owner

1. In Google Cloud console → APIs & Services → Credentials, open the key and set
   **API restrictions** to *Generative Language API* only.
2. Set **Application restrictions** to *Android apps* and add the package name
   `app.haven.companion` with the SHA-1 of the signing certificate.
3. Set a daily quota / budget alert on the project.
4. Keep the repository private, or remember that GitHub Actions artifacts of a public
   repository can be downloaded by any signed-in GitHub user.

## Known limitations / accepted risks

1. **Key extraction.** Anything shipped inside an APK can be extracted. The app
   restriction above makes an extracted key useless outside this app; the quota caps
   the damage. A server-side proxy would remove the key from the APK entirely.
2. **Free-tier data use.** On Gemini's free tier Google may use content to
   improve its products. Users who want stronger guarantees can enable billing
   on their Google project.
3. **Keyword safety layer** can miss novel phrasings or over-flag. It is a
   backstop alongside the model's own instructions.
4. **Sideloaded builds** are signed with the CI machine's key unless a release keystore
   is configured, so a new build may need the old one uninstalled first. Play Protect
   warns about any sideloaded app from an unknown developer.
5. Code shrinking/obfuscation is off for sideloaded builds and will be enabled for the
   Play Store build.
