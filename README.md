# Real-Time Translation (Live Translate)

[中文文档](README.zh-CN.md)

An **Android real-time subtitles** app: captures audio playing on your phone (media) or the microphone, recognizes speech **on-device** with the SenseVoice model, translates it, and shows live captions in a **draggable floating overlay**.

- **Fully on-device, offline ASR** — SenseVoice model (~230 MB) is bundled inside the APK; audio never leaves your phone
- Translation via **DeepSeek** (streaming, high quality) or the **free Microsoft endpoint** (no key required)
- Finished sessions are kept in **History** and can be exported as `.txt`

---

## Features

| Area | Description |
|------|-------------|
| **Subtitles tab** | Source / target language (remembered), audio source, start / stop, status and preview |
| **Audio source** | Media / microphone / media+mic (remembered; mic-only skips screen-capture permission) |
| **On-device ASR** | Alibaba SenseVoice int8 model, bundled in the APK, unpacked on first launch, works offline |
| **Translation engine** | DeepSeek (streaming SSE; model & API URL configurable) / Microsoft (free, no key) |
| **Floating overlay** | Over other apps; thin top grabber to move; corner handle to resize (font size unchanged) |
| **Display modes** | Source + translation (split panes with a divider), source only, or translation only |
| **Auto-scroll** | Separate panes for source / translation; **scrolls one line only when a line wraps** |
| **Audio capture** | Media: `MediaProjection` + `AudioPlaybackCapture`; mic: `AudioRecord` → 16 kHz PCM |
| **History** | Sessions saved per your setting (auto-clean / save-all, max 20); expand, long-press to copy, save each as txt, clear all |
| **Export** | After stop, save this session's source + translation as `.txt` to Downloads |
| **Translated voice** | Toggle & volume (off by default; volume can go past 100% to sit above the original) |
| **Settings** | Engine & API key, model status/repair, subtitle appearance (font size, background opacity, source/translation text colors, display mode), voice, history policy, permissions, about |
| **Language** | Follows the system: Chinese device → Chinese UI; otherwise → English |

---

## Requirements

### Using the app

- Android **10+** (API 29, required for system audio capture)
- DeepSeek: a [DeepSeek API key](https://platform.deepseek.com/); Microsoft engine needs none

### Building from source

- JDK **17+**, Android SDK as required by the project (compileSdk **37**, etc.)
- Android Studio or command-line Gradle
- **Fetch the bundled model first** (see [Build](#build)) — it is ~240 MB, above GitHub's 100 MB per-file limit, so it is **not** committed

---

## Install & use

### 1. Install

- Install a release build from [GitHub Releases](../../releases) when available
- Or build locally (see [Build](#build))

### 2. Configure the translation engine

1. Open the app → **Settings**
2. Pick an engine:
   - **DeepSeek**: enter your API key (optionally change the model / API base URL)
   - **Microsoft (free)**: no key needed, ready to go
3. Tap **Save and test connection**

### 3. Start subtitles

1. Open the **Subtitles** tab
2. Choose **source** (auto-detect works; for Japanese/Korean etc. a source-language change takes effect after a session restart) and **target** languages
3. Choose **audio source** (media / microphone / both)
4. Tap **Start subtitles**
5. Grant when prompted:
   - **Display over other apps** (overlay)
   - **Screen capture / cast** if media is selected
   - **Microphone** if mic is selected
6. Play foreign-language media or speak into the mic; the translation appears in the floating window

After you stop, if there is content, use **Save this session as .txt** on the subtitles tab (saved to Downloads, e.g. `8月13日-14:30-翻译结果.txt`). Sessions are also written to the History tab per your history setting.

### 4. Overlay tips

- Drag the **thin top bar** to move
- Drag the **corner handle** to resize (font size unchanged)
- In Settings: font size, background opacity, source/translation text colors, display mode, reset appearance
- In History: long-press a source/translation block to copy; expand and "Save as .txt" per entry

---

## Build

```bash
# Windows Git Bash example: set JDK
export JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-17.0.11_9"

# If local.properties is missing, set the SDK path, e.g.:
# sdk.dir=C:/Users/YOUR_NAME/AppData/Local/Android/Sdk

# 1. Fetch the bundled model first (required for release; debug recognition needs it too)
bash scripts/fetch-models.sh

# 2. Build debug
./gradlew :app:assembleDebug
```

Output:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Release is the same (`./gradlew :app:assembleRelease`), but it **fails without `fetch-models.sh`** — a `preReleaseBuild` guard enforces that the model is bundled. An APK with the bundled model is ~419 MB.

Or open the **repository root** in Android Studio → Sync → Run.

> If the project path contains non-ASCII characters, the project sets `android.overridePathCheck=true`.

---

## Project structure

```text
app/src/main/java/com/xzm/realtimetranslate/
  ui/           # Subtitles, Settings, History tabs, theme (MIUIX)
  service/      # Foreground session service
  overlay/      # Floating caption window
  audio/        # Media capture / mic / mix + translated audio playback
  live/         # Realtime session client (ASR + translation orchestration)
  translate/    # ASR engine / DeepSeek / Microsoft free translation
  data/         # DataStore settings, history, API key storage
  util/         # txt export, model download, permission utils
```

The app depends on MIUIX from **Maven Central**.

---

## Privacy

- **Recognition runs fully on-device** (bundled SenseVoice model); audio is never uploaded
- Recognized text is sent only to the translation engine you choose in Settings (DeepSeek / Microsoft)
- The API key stays on device (`EncryptedSharedPreferences` when available)
- This project has **no** backend that collects keys or audio
- Do not commit `local.properties`, secrets, or signing keys

---

## Known limitations

- Some apps / DRM content **block** playback capture → nothing to translate in media mode (try microphone)
- Recognition depends on the SenseVoice model; accents/noise may cause errors (inherent model limits)
- DeepSeek context-aware translation uses the **last 4 sentences** only
- A source-language change takes effect after **restarting a subtitle session**
- "Save all" caps at 20 sessions; the oldest is dropped

---

## Third-party

See [NOTICE](NOTICE). Primary UI dependency: [MIUIX](https://github.com/compose-miuix-ui/miuix) (Apache-2.0).

---

## License

[Apache License 2.0](LICENSE)

---

## Acknowledgements

- [Linux.do](https://linux.do)
- This project is a fork of [luoxiaoxin123/live-translate](https://github.com/luoxiaoxin123/live-translate) (Apache 2.0), with on-device ASR, DeepSeek/Microsoft translation, history, and other changes
