# Building the APK

The app needs three **public** values at build time. None of them is a
secret; the OpenAI key never goes into the app.

| Name | Example |
| --- | --- |
| `HAVEN_BACKEND_URL` | `https://haven-backend.onrender.com` |
| `HAVEN_SUPABASE_URL` | `https://abcd1234.supabase.co` |
| `HAVEN_SUPABASE_ANON_KEY` | the project's anon / publishable key |

They are read from `android/local.properties`, Gradle properties (`-P`) or
environment variables, in that order.

## Option A: GitHub Actions (no Android Studio needed)

1. In the GitHub repo: **Settings → Secrets and variables → Actions →
   Variables**, add the three variables above.
2. **Actions → Android → Run workflow** (or push a change under `android/`).
3. Open the finished run and download the **haven-debug-apk** artifact.
4. Copy the APK to your phone and open it. Android will ask you to allow
   installs from that source.

The debug APK is signed with a throwaway debug key, which is fine for
personal use. For a release build signed with your own key:

```bash
keytool -genkeypair -v -keystore haven-release.jks -keyalg RSA -keysize 4096 \
  -validity 10000 -alias haven
base64 -w0 haven-release.jks   # paste into the HAVEN_KEYSTORE_BASE64 secret
```

Add secrets `HAVEN_KEYSTORE_BASE64`, `HAVEN_KEYSTORE_PASSWORD`,
`HAVEN_KEY_ALIAS`, `HAVEN_KEY_PASSWORD`, then push a tag like `v0.1.0`. The
workflow uploads **haven-release-apk**. Keep the keystore safe: updates must be
signed with the same key.

## Option B: Android Studio / command line

Requirements: Android Studio (or the Android SDK with platform 35) and JDK 17.

```bash
cd android
cat > local.properties <<'PROPS'
sdk.dir=/path/to/Android/sdk
HAVEN_BACKEND_URL=https://your-backend.example.com
HAVEN_SUPABASE_URL=https://YOUR-PROJECT.supabase.co
HAVEN_SUPABASE_ANON_KEY=your-anon-key
PROPS
./gradlew testDebugUnitTest assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

`local.properties` is git-ignored.

### Talking to a backend on your computer

Debug builds allow plain HTTP only to the emulator host and localhost.

- **Emulator:** the default `HAVEN_BACKEND_URL` is `http://10.0.2.2:8000`.
- **Physical phone over USB:** run `adb reverse tcp:8000 tcp:8000` and build
  with `HAVEN_BACKEND_URL=http://localhost:8000`.

Release builds require HTTPS.

## First run

1. Read the intro and the "not a therapist" notice, then the privacy summary.
2. Enter your name, choose whether memory is on, allow the microphone.
3. Create an account (or sign in).
4. The voice screen greets you and starts listening (you can turn auto-start
   off in Settings). Tap the orb to talk; tap **End conversation** to finish.

## Troubleshooting

| Symptom | Likely cause |
| --- | --- |
| "This build is missing its Supabase configuration" | `HAVEN_SUPABASE_URL` / `HAVEN_SUPABASE_ANON_KEY` were empty at build time |
| "I'm having trouble connecting right now…" | No internet, wrong `HAVEN_BACKEND_URL`, backend asleep (free tier) or down |
| "The voice service is unavailable right now" | Backend can't mint a realtime session: check `OPENAI_API_KEY`, model name, billing |
| Signed out repeatedly | Backend `SUPABASE_JWT_SECRET` doesn't match the project (or leave it empty to use JWKS) |
| Companion is very quiet / uses the earpiece | Check media/call volume; plug in headphones if the speaker echoes |
