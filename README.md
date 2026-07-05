# android-roborazzi

A small Jetpack Compose demo app showing how to use [Roborazzi](https://github.com/takahirom/roborazzi) for screenshot regression testing on the JVM with Robolectric — no emulator required.

The app navigates between a home screen, an item list, item details, and a not-found screen. Tests capture golden images and verify them on every run.

## Stack

- Kotlin 2.4, Compose, Navigation Compose
- Roborazzi 1.64 + Robolectric 4.16
- JDK 21
- Golden images stored as WebP in `app/src/screenshots/`

## Prerequisites

- JDK 21
- Android SDK (for Gradle/AGP)

## Run the app

```bash
./gradlew :app:installDebug
```

## Run tests

Verify screenshot tests (default — compares against committed golden images):

```bash
./gradlew :app:testDebugUnitTest
```

View the Roborazzi HTML report after a run:

```bash
open app/build/reports/roborazzi/debug/index.html
```

## Update golden images

When you intentionally change the UI, record new goldens:

```bash
./gradlew :app:testDebugUnitTest \
  -Proborazzi.test.verify=false \
  -Proborazzi.test.record=true
```

Commit the updated `app/src/screenshots/*.webp` files together with your code changes.

## What's tested

| Test class | Coverage |
|---|---|
| `HomeScreenTest` | Home screen (light + dark theme) |
| `ItemListScreenTest` | List scroll positions (top, middle, bottom) |
| `ItemDetailScreenTest` | Sample item and long description |
| `AppNavHostTest` | Navigation flows with interaction assertions + screenshots |

Tests share a common setup in `RoborazziComposeTest` (Pixel 5, SDK 33, native graphics, WebP output). Screenshot names live in `GoldenImages.kt`.

## CI

GitHub Actions runs Roborazzi tests on every push to `main` and on pull requests.

- **Workflow:** [`.github/workflows/roborazzi.yml`](.github/workflows/roborazzi.yml)
- **Runner:** `macos-latest` with JDK 21
- **On failure:** uploads a `roborazzi-diffs` artifact with compare images and the HTML report

### PR diff comments

When a pull request fails screenshot tests, [`.github/workflows/roborazzi-comment.yml`](.github/workflows/roborazzi-comment.yml) posts a comment on the PR with inline diff images (`*_compare.webp`). No need to download artifacts for a quick visual review.

For the full report, download the `roborazzi-diffs` artifact from the failed run and open `reports/roborazzi/debug/index.html`.

## Project layout

```
app/
├── src/screenshots/                # Golden images (committed)
├── src/main/                       # Compose UI, navigation, voice session
├── src/test/                       # Roborazzi JVM screenshot tests
│   ├── RoborazziComposeTest.kt     # Shared test base class
│   ├── GoldenImages.kt             # Screenshot name constants
│   └── *Test.kt                    # Per-screen and navigation tests
└── src/androidTest/                # Instrumented voice E2E (emulator + live API)
    └── voice/
        ├── VoiceAppIntegrationTest.kt
        └── support/                # UiAutomator robot + TTS test harness
```

## Voice assistant (Grok Voice Agent API)

The app includes a voice overlay for hands-free navigation, screen description, and web search via the [xAI Voice Agent API](https://docs.x.ai/developers/model-capabilities/audio/voice-agent).

### Setup

1. Set your API key before building (baked into `BuildConfig` at compile time):

```bash
export XAI_API_KEY=your-key-here
./gradlew :app:installDebug
```

2. Run on an emulator or device with microphone access.
3. Toggle **Connect** on the voice overlay and grant **Record audio** when prompted.
4. Grok greets you, then always-on listening starts (server VAD).
5. Ask naturally — no need to name tools. For example:
   - "Describe the screen for me"
   - "What is the weather in Munich?"
   - "Go to the items list"
   - "Scroll to item 10"
   - "Go back"

The overlay shows live **You** / **Grok** transcript turns, mic level, status, and the last tool invoked.

### Voice tools

| Tool | Where it runs | Purpose |
|---|---|---|
| `web_search` | xAI server | Weather, news, and other live web facts |
| `navigate_to_screen` | App | Home, items list, item detail (valid or not-found) |
| `navigate_back` | App | Pop navigation stack |
| `open_list_item` | App | Scroll list to a 1-based item index |
| `describe_screen` | App | Read structured UI tree for the current screen |

Session config uses `grok-voice-latest`, voice `eve`, server VAD, and an **Answer brief.** instruction for short spoken replies.

### Voice integration test (emulator)

A single instrumented E2E test connects to the live API, exercises every nav screen and voice tool, validates conversation turn order (You → Grok), and disconnects. Requires `XAI_API_KEY`, a running emulator/device, and network.

```bash
adb shell am force-stop com.example.roborazzidemo

./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.example.roborazzidemo.voice.VoiceAppIntegrationTest
```

The test uses emulator TTS plus debug `VOICE_SPOKEN` injects to simulate user speech. Typical runtime is 2–5 minutes.

Optional: disable TTS playback (inject only) with `-Pandroid.testInstrumentationRunnerArguments.disableTestSpeechPlayback=true`.

### Debug broadcasts (debug builds)

| Action | Purpose |
|---|---|
| `com.example.roborazzidemo.VOICE_SPOKEN` | Inject a spoken user turn (`text` extra) |
| `com.example.roborazzidemo.VOICE_TEXT` | Inject a text turn (direct-speech debug path) |
| `com.example.roborazzidemo.VOICE_DISCONNECT` | Disconnect the voice session |

## License

Demo project — use as a reference for your own Roborazzi setup.