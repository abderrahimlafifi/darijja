# Talk Translate

Two way voice conversation translator between Arabic and English, French or Spanish.
Made for the Poco X3 NFC (Android 10 to 12, MIUI), works on any Android 7.0 or newer.

- Green button: you speak Arabic (Moroccan Darija, Modern Standard Arabic or Egyptian). The app translates and reads the translation aloud.
- Blue button: the other person speaks English, French or Spanish. The app translates into Arabic and reads it aloud.
- Tap a bubble to hear it again. Long press a bubble to copy the translation.
- No API keys and no paid services. Only an internet connection is needed.

## Download

Latest APK (built by GitHub Actions on every push):

https://github.com/abderrahimlafifi/darijja/releases/latest/download/TalkTranslate.apk

All builds: https://github.com/abderrahimlafifi/darijja/releases

## Install on the phone

1. Open the link above in Chrome on the phone and download `TalkTranslate.apk`.
   (If the repository is private, sign in to GitHub in the browser first.)
2. Open the downloaded file from the notification or from the Files app.
3. MIUI will ask to allow installs from this source: tap Settings, turn on "Allow from this source", go back and tap Install.
4. If MIUI shows a security scan warning, tap "Install anyway". The APK is debug signed, which is normal for a personal app.
5. Make sure these are installed and up to date from the Play Store:
   - **Google** app (used for speech recognition)
   - **Speech Services by Google** (used for reading aloud)
6. Install the voices: Settings > Additional settings > Languages & input > Text-to-speech output,
   choose **Speech Services by Google** as the preferred engine, tap the gear icon > Install voice data,
   and download Arabic plus English, French or Spanish.
   (On some MIUI versions the path is Settings > Text-to-speech > Google.)
7. Open Talk Translate, pick the other language and your Arabic dialect, and press the green button.

New builds use the same signing key, so they install over the old version without uninstalling.

## Build it yourself

Requirements: JDK 17 and the Android SDK (platform 34).

```
./gradlew assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

Versions: Gradle 8.9, Android Gradle Plugin 8.5.2, compileSdk 34, targetSdk 34, minSdk 24.
Java only, single Activity, no third party libraries.

## How it works

| Step | Service |
| --- | --- |
| Speech to text | Android `RecognizerIntent.ACTION_RECOGNIZE_SPEECH` (Google app), with `EXTRA_LANGUAGE` set to `ar-MA`, `ar-SA`, `ar-EG`, `en-US`, `fr-FR` or `es-ES` |
| Translation | `translate.googleapis.com/translate_a/single?client=gtx` with fallback to `api.mymemory.translated.net`, on a background thread with 10 second timeouts |
| Text to speech | Android `TextToSpeech`, preferring the Google engine when installed |

The manifest declares `INTERNET`, `ACCESS_NETWORK_STATE` (to detect no internet) and `<queries>` entries
for `RECOGNIZE_SPEECH`, `RecognitionService` and `TTS_SERVICE`, needed for package visibility on Android 11 and newer.

`app/debug.keystore` is a standard debug key (passwords `android`) kept in the repo so every build has the same signature.
It is not meant for Play Store publishing.

## Project layout

```
app/src/main/AndroidManifest.xml
app/src/main/java/com/talktranslate/app/MainActivity.java   UI, speech, TTS
app/src/main/java/com/talktranslate/app/Translator.java     Google and MyMemory translation
app/src/main/res/                                           theme, colors, launcher icon
.github/workflows/build.yml                                 builds the APK and publishes the release
```
