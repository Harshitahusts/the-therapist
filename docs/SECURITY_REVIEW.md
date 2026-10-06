# Security and privacy review

Review of the MVP (October 2026). Scope: backend API, database schema, Android
client, build/CI configuration.

## Secrets

| Check | Result |
| --- | --- |
| OpenAI API key only on the server | Yes. The app receives a per-conversation client secret that expires 2 minutes after minting (only needed to open the WebRTC call). |
| Supabase service-role key only on the server | Yes; used solely for account deletion. |
| Values compiled into the APK | Backend URL, Supabase URL, Supabase anon key. All public by design. |
| Secrets in the repository | None. `.env`, `local.properties`, keystores are git-ignored; CI uses repository secrets. |

## Authentication and authorization

- Every `/v1` endpoint requires a Supabase access token. Tokens are verified
  with a pinned algorithm list (HS256 with the project secret, or RS256/ES256
  via JWKS), audience `authenticated`, required `exp` and `sub`. Tokens with
  another role (e.g. `service_role`) are rejected.
- Every query is scoped by the user id from the verified token. Conversation
  endpoints return the same 404 for "doesn't exist" and "belongs to someone
  else". Vector searches filter by user id before ranking.
- Tests cover cross-user access to memories, conversations, tools, and data
  deletion (`tests/test_memory.py`, `tests/test_conversations.py`).
- Row-level security is enabled on every table so Supabase's auto-generated
  REST API exposes nothing; the backend connects as the table owner.

## Data minimisation

- No audio is stored. Transcripts are held in memory on the phone, sent once
  at the end of a conversation for summarising, and not persisted.
- The safety endpoint classifies and discards the text; only level and
  category are stored.
- Memories pass a sensitive-data filter (passwords, API keys, card/account
  numbers, ID numbers), both when saved by the voice tool, by extraction and
  by the user.
- Error responses and logs never include request bodies or model output.
- On the device, tokens live in EncryptedSharedPreferences (Keystore-backed),
  excluded from cloud backup and device transfer.

## Transport

- Release builds allow HTTPS only (network security config). Debug builds
  allow cleartext only to the emulator host and localhost.
- Voice uses WebRTC (DTLS-SRTP) directly between the phone and OpenAI.

## Abuse and cost controls

- Per-user rate limit on new voice sessions (`REALTIME_SESSIONS_PER_HOUR`).
- Capped response length and a 50-minute conversation limit in the app.
- Input size limits on every request model.
- API docs are disabled in production.

## Known limitations / accepted risks

1. **Client-side session control.** The phone holds the realtime connection,
   so a modified client could change the session instructions or skip the
   safety relay. For a single-user personal app this only affects the user
   themself. Mitigation path: move to OpenAI's server-side ("sideband")
   control channel so the backend owns instructions, tools and safety
   injection.
2. **Rate limiter is in-memory**, per process. Fine for one instance; use a
   shared store if scaled out.
3. **Keyword safety layer** can miss novel phrasings or over-flag. It is a
   backstop alongside moderation and the model's own instructions, and is
   covered by an evaluation set that should keep growing.
4. **Third-party processing.** OpenAI processes audio and text; this is
   disclosed in onboarding and the privacy policy.
5. **Account deletion** deletes the database rows first, then the Supabase
   login; if the second step fails the user is told and can retry.
6. The Android build has not been run on a device as part of this review; CI
   builds it and runs its unit tests.
