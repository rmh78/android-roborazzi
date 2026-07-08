# android-roborazzi

A reference project for **two complementary Android test strategies** on the same codebase: [Roborazzi](https://github.com/takahirom/roborazzi) JVM screenshot regression for Compose UI, and instrumented emulator E2E for live voice-agent integration.

The demo app navigates between a home screen, an item list, item details, and a not-found screen, with an optional Grok Voice Agent overlay for hands-free navigation.

**For AI agents & contributors:** see [AGENTS.md](AGENTS.md) for architecture, conventions, and detailed technical reference.

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

Tests share a common setup in `RoborazziComposeTest` (Pixel 5, SDK 33, native graphics, WebP output). Screenshot names live in `GoldenImages.kt`. See [docs/screenshot-testing.md](docs/screenshot-testing.md) for full coverage, record/verify workflow, and CI details.

## CI

GitHub Actions runs Roborazzi tests on every push to `main` and on pull requests.

- **Workflow:** [`.github/workflows/roborazzi.yml`](.github/workflows/roborazzi.yml)
- **Runner:** `macos-latest` with JDK 21
- **On failure:** uploads a `roborazzi-diffs` artifact with compare images and the HTML report

### PR diff comments

When a pull request fails screenshot tests, [`.github/workflows/roborazzi-comment.yml`](.github/workflows/roborazzi-comment.yml) posts a comment on the PR with inline diff images (`*_compare.webp`). No need to download artifacts for a quick visual review.

For the full report, download the `roborazzi-diffs` artifact from the failed run and open `reports/roborazzi/debug/index.html`. See [docs/ci-and-development.md](docs/ci-and-development.md) for workflow and script details.

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
        ├── EmulatorVoiceSetupTest.kt
        └── support/                # UiAutomator robot + PCM speech driver
```

See [docs/architecture.md](docs/architecture.md) for composition root, navigation graph, and how both test harnesses share the same UI.

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

### Emulator vs physical device

Voice behaves very differently on an AVD than on a real phone. Emulators use **half-duplex** (mic muted while Grok speaks; no barge-in) to avoid echo loops. Physical devices use **full-duplex** with hardware AEC and barge-in for natural conversation.

Expect turn-taking on emulators: wait for **Listening — ask a question** before you speak. Use headphones when testing on an AVD.

See [docs/voice-assistant.md](docs/voice-assistant.md) for the echo-loop diagram, audio routing details, debug broadcasts, and tool execution paths.

### Voice integration test (emulator)

Two instrumented test classes in `com.example.roborazzidemo.voice`:

| Test | Role |
|------|------|
| `EmulatorVoiceSetupTest` | Fast emulator voice infra checks (capture, half-duplex, PCM ping) |
| `VoiceAppIntegrationTest` | Live agent E2E — every nav screen, all voice tools, turn order |

Requires `XAI_API_KEY`, a running emulator/device, and network.

```bash
adb shell am force-stop com.example.roborazzidemo

# Full package (~3 min) — CI default
bash scripts/run-voice-integration-test.sh

# Fast smoke (~1 min)
bash scripts/run-voice-integration-test-short.sh
```

User speech is simulated via runtime TTS→PCM synthesis streamed through `input_audio_buffer.append` (server VAD + ASR). **Grok replies are audible** on the emulator speaker. User prompts are **silent by default**; opt in locally to hear the **same PCM bytes** mirrored to the speaker:

```bash
-Pandroid.testInstrumentationRunnerArguments.voiceE2eAudiblePrompts=true
```

Combine with short smoke for a ~1 min audible demo run. See [docs/voice-e2e-testing.md](docs/voice-e2e-testing.md) for all runner arguments (`voiceE2eShort`, `requireHostMic`, etc.).

**CI:** Pull requests run the full voice package on a hardware-accelerated emulator (silent user prompts). Add an `XAI_API_KEY` [repository secret](https://docs.github.com/en/actions/security-for-github-actions/security-guides/using-secrets-in-github-actions); without it the workflow fails fast. See [docs/ci-and-development.md](docs/ci-and-development.md) for workflow details.

### Debug broadcasts (debug builds)

| Action | Purpose |
|---|---|
| `com.example.roborazzidemo.VOICE_PCM_SPEAK` | Synthesize and stream a user utterance as PCM (`text` extra; optional `mirror_pcm` for speaker playback) |
| `com.example.roborazzidemo.VOICE_PCM_BYTES` | Stream raw PCM bytes (`pcm` extra, Base64; optional `mirror_pcm`) |
| `com.example.roborazzidemo.VOICE_TEXT` | Inject a text turn (direct-speech debug path) |
| `com.example.roborazzidemo.VOICE_DISCONNECT` | Disconnect the voice session |

Full broadcast list and tool architecture: [docs/voice-assistant.md](docs/voice-assistant.md).

## License

Demo project — use as a reference for your own Roborazzi setup.