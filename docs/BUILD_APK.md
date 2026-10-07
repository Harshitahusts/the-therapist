# Getting and building the APK

Haven needs **no build-time configuration**: no server URL, no keys. You paste
your own free Gemini API key into the app on first launch.

## 1. Get a free Gemini API key

1. Go to https://aistudio.google.com/apikey and sign in with a Google account.
2. Click **Create API key** and copy it.

Keep it private. Anyone with the key can use your Gemini quota.

## 2a. Download the APK from GitHub (no tools needed)

1. In the GitHub repo, open **Actions → Android**.
2. Open the latest run with a green tick (or click **Run workflow** to build one).
3. At the bottom, download **haven-debug-apk** (a zip containing `app-debug.apk`).
4. Copy the APK to your phone and open it. Android asks you to allow installs
   from that app (browser or file manager); allow it.

The debug APK is signed with a throwaway debug key, which is fine for personal
use. To update later, install a newer debug APK from the same workflow.

### Signed release build (optional)

```bash
keytool -genkeypair -v -keystore haven-release.jks -keyalg RSA -keysize 4096 \
  -validity 10000 -alias haven
base64 -w0 haven-release.jks   # paste into the HAVEN_KEYSTORE_BASE64 secret
```

Add repository secrets `HAVEN_KEYSTORE_BASE64`, `HAVEN_KEYSTORE_PASSWORD`,
`HAVEN_KEY_ALIAS`, `HAVEN_KEY_PASSWORD`, then push a tag like `v0.2.0`. The
workflow uploads **haven-release-apk**. Keep the keystore safe: updates must be
signed with the same key.

## 2b. Build it yourself

Requirements: Android Studio (or the Android SDK with platform 35) and JDK 17.

```bash
cd android
echo "sdk.dir=/path/to/Android/sdk" > local.properties
./gradlew testDebugUnitTest assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Or open `android/` in Android Studio and press **Run** (an emulator works too;
it uses your computer's microphone).

## First run

1. Read the intro, the "not a therapist" notice and the privacy summary.
2. Enter your name and choose whether memory is on.
3. Paste your Gemini key and tap **Check key and continue**. Haven checks the
   key and picks the best live voice model it can use.
4. Allow the microphone.
5. The voice screen greets you and starts listening (auto-start can be turned
   off in Settings). Tap **End conversation** to finish.

## Watching it on a computer

To mirror your phone's screen on your laptop (demos, recordings), install
[scrcpy](https://github.com/Genymobile/scrcpy), enable USB debugging on the
phone, plug it in and run `scrcpy`.

## Troubleshooting

| Message / symptom | Likely cause |
| --- | --- |
| "That Gemini API key isn't valid." | Key mistyped or deleted; create a new one in AI Studio |
| "…can't use Gemini's live voice models yet" | Your key/project has no Live API model available; try a new key or later |
| "Gemini's free limit was reached" | Free-tier quota used up for now; wait and try again |
| "The selected voice model isn't available" | Pick another in Settings → Voice model |
| "I'm having trouble connecting right now…" | No internet, or Gemini unreachable |
| The companion hears itself / keeps interrupting itself | Loudspeaker echo; use headphones or lower the volume |
| Companion is quiet / uses the earpiece | Check call volume (Haven uses the voice-call audio path) |
