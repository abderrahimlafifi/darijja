# Darijja Android apps

This repository holds two Android apps, built by GitHub Actions on every push:

| App | APK | Folder |
| --- | --- | --- |
| **متصفح نقي / Clean Browser**: ad blocking browser with favorites, for phones and TVs | [CleanBrowser.apk](https://github.com/abderrahimlafifi/darijja/releases/latest/download/CleanBrowser.apk) | `browser/` |
| **Talk Translate**: Arabic voice conversation translator | [TalkTranslate.apk](https://github.com/abderrahimlafifi/darijja/releases/latest/download/TalkTranslate.apk) | `app/` |

All builds: https://github.com/abderrahimlafifi/darijja/releases

---

# متصفح نقي (Clean Browser)

A web browser that blocks ads, trackers, pop-ups and ad redirects, with a favorites screen.
Made for the Poco X3 NFC (MIUI, Android 10 to 12) and the Xiaomi TV A 55" (Android TV / Google TV, remote control only).
Works on any phone, tablet or TV with Android 5.0 or newer. Arabic interface.

## What it blocks

| Layer | How |
| --- | --- |
| Ad and tracker servers | Every request the page makes is checked against a list of about 76,000 ad, tracker and malware domains ([StevenBlack/hosts](https://github.com/StevenBlack/hosts), MIT) plus a few extra pop-up ad networks. A domain also blocks its subdomains. Requests from service workers are checked too. |
| Fresh lists | Once a week the app downloads the latest StevenBlack list and the AdGuard DNS filter in the background. Settings > "تحديث قائمة الحظر الآن" updates immediately. |
| Pop-ups and pop-unders | New windows opened without a tap are refused. A tap that opens a window to another site (the usual "click anywhere, get an ad tab" trick) is blocked, with a small bar to open it anyway. Real `target="_blank"` links and same site windows open normally in the same screen. |
| Ad redirects | A page that jumps to an ad domain, or tries to open the Play Store or another app without a tap, is stopped. Opening another app always asks first. |
| Click traps | Invisible full screen layers that ad scripts put over a page are removed. |
| Empty ad boxes | Common ad containers (AdSense, GPT, Taboola, Outbrain, MGID, ...) are hidden. |
| YouTube | Video ads are muted and skipped, "Skip" is pressed automatically, ad cards are hidden. YouTube changes often, so this part may need updates. |
| Other annoyances | No "leave this page?" traps, no location or camera prompts, no web notifications. |

The shield button shows how many things were blocked on the page. From it, or from the menu, blocking can be turned off for one site if that site breaks.

## Favorites

- The home screen shows the favorites as big tiles (Google, YouTube and Wikipedia to start).
- The star in the toolbar adds or removes the open page. The "+" tile adds a site by hand.
- Long press a tile (on the TV: hold OK) to open, edit, move or delete it.
- Long press a link in a page to add it to favorites.

## Using it on the TV

Arrow keys move a mouse pointer over the page; it speeds up while a key is held.

| Remote | Action |
| --- | --- |
| Arrows | Move the pointer. At the edge of the screen the page scrolls. |
| OK | Click. Hold OK for a long press (link menu). |
| Up at the top of the page | Go to the address bar and the buttons. Down comes back to the page. |
| Back | Previous page, then the home screen, then press twice to exit. |
| Play/pause | Plays or pauses the video on the page. |

On the TV the address bar opens a keyboard dialog. Videos go full screen with the player's full screen button.
The pointer can be turned off, and its speed changed, in Settings.

## Install on the phone (Poco X3 NFC)

1. Open [CleanBrowser.apk](https://github.com/abderrahimlafifi/darijja/releases/latest/download/CleanBrowser.apk) in Chrome on the phone and download it.
2. Open the downloaded file. MIUI asks to allow installs from Chrome: tap Settings, turn on "Allow from this source", go back and tap Install.
3. If MIUI shows a security scan warning, tap "Install anyway" (the APK is debug signed, normal for a personal app).
4. Optional: make it the default browser in Settings > Apps > Manage apps > Default apps > Browser.

## Install on the TV (Xiaomi TV A 55")

Google TV only installs apps from other sources after developer mode is on:

1. Settings > System > About, then press OK 7 times on "Android TV OS build" until it says you are a developer.
2. Install the free **Downloader** app (by AFTVnews) from the Play Store on the TV.
3. Settings > Apps > Security & restrictions > Unknown sources, and allow **Downloader**.
4. Open Downloader, type `https://github.com/abderrahimlafifi/darijja/releases/latest/download/CleanBrowser.apk`, press Go, then Install.
5. The app appears in Your apps (Apps > See all apps) with its banner.

If the repository is private the TV cannot download from GitHub. Then copy `CleanBrowser.apk` to a USB stick,
plug it into the TV and open it with a file manager app from the Play Store (for example "File Commander"), which also needs the Unknown sources permission.

New builds use the same signing key, so they install over the old version without losing favorites.

## طريقة الاستعمال باختصار

- **الهاتف:** حمّل CleanBrowser.apk من الرابط أعلاه، افتحه، واسمح بالتثبيت من هذا المصدر.
- **التلفاز:** فعّل وضع المطوّر (الإعدادات > النظام > حول > اضغط 7 مرات على "Android TV OS build")، ثبّت تطبيق Downloader من متجر Play، اسمح له بتثبيت التطبيقات من مصادر غير معروفة، ثم اكتب فيه رابط CleanBrowser.apk.
- **الريموت:** الأسهم تحرك المؤشر، زر OK للنقر، السهم للأعلى في أعلى الصفحة للوصول إلى شريط العنوان.
- **المفضلة:** زر النجمة يضيف الصفحة، والضغط المطوّل على أي موقع في الصفحة الرئيسية يسمح بتعديله أو حذفه أو تغيير ترتيبه.
- **إذا لم يعمل موقع:** اضغط على زر الدرع ثم "إيقاف الحظر في هذا الموقع".

## Build and project layout

Requirements: JDK 17 and the Android SDK (platform 34).

```
./gradlew :browser:assembleDebug :browser:testDebugUnitTest
```

The APK is written to `browser/build/outputs/apk/debug/browser-debug.apk`.
minSdk 21, targetSdk 34, Java only, no third party libraries.

```
browser/src/main/AndroidManifest.xml                         phone + TV launcher (LEANBACK), banner, link handling
browser/src/main/java/com/cleanbrowser/app/MainActivity.java WebView, toolbar, pop-up rules, favorites screen, remote control
browser/src/main/java/com/cleanbrowser/app/AdBlocker.java    loads, matches and updates the block lists
browser/src/main/java/com/cleanbrowser/app/BlockList.java    domain list parser and matcher (unit tested)
browser/src/main/java/com/cleanbrowser/app/Scripts.java      scripts injected into pages (ad boxes, click traps, YouTube, scrolling)
browser/src/main/java/com/cleanbrowser/app/CursorView.java   TV mouse pointer
browser/src/main/java/com/cleanbrowser/app/Favorites.java    favorites and site icons
browser/src/main/java/com/cleanbrowser/app/SettingsActivity.java
browser/src/main/assets/blocklist.txt                        bundled StevenBlack list (license in blocklist-license.txt)
browser/src/test/java/com/cleanbrowser/app/BlockListTest.java
```

---

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
   The first time, allow microphone access. Speak, then tap the same button again when you finish (or just stop talking).
   Under "Listening mode" you can choose "In the background" (no popup) or "Google window". If background listening fails, the app retries once and then opens the Google window automatically.

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
| Speech to text | Android `SpeechRecognizer` running in the background inside the app (no Google popup), using Google's recognition service, with `EXTRA_LANGUAGE` set to `ar-MA`, `ar-SA`, `ar-EG`, `en-US`, `fr-FR` or `es-ES` |
| Translation | `translate.googleapis.com/translate_a/single?client=gtx` with fallback to `api.mymemory.translated.net`, on a background thread with 10 second timeouts |
| Text to speech | Android `TextToSpeech`, preferring the Google engine when installed |

The app asks once for microphone permission. If background recognition is not available it falls back to the system `RecognizerIntent` dialog.

The manifest declares `INTERNET`, `RECORD_AUDIO`, `ACCESS_NETWORK_STATE` (to detect no internet) and `<queries>` entries
for `RECOGNIZE_SPEECH`, `RecognitionService` and `TTS_SERVICE`, needed for package visibility on Android 11 and newer.

`app/debug.keystore` is a standard debug key (passwords `android`) kept in the repo so every build has the same signature.
It is not meant for Play Store publishing.

## Talk Translate project layout

```
app/src/main/AndroidManifest.xml
app/src/main/java/com/talktranslate/app/MainActivity.java   UI, speech, TTS
app/src/main/java/com/talktranslate/app/Translator.java     Google and MyMemory translation
app/src/main/res/                                           theme, colors, launcher icon
.github/workflows/build.yml                                 builds the APK and publishes the release
```
