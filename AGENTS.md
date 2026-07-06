# AGENTS.md

Technical reference for AI agents and contributors working in this repository.

## Project purpose

**android-roborazzi** is two things at once:

1. **Demo app** — a Jetpack Compose navigation sample (home → items list → item detail / not-found) with an optional Grok Voice Agent overlay for hands-free navigation.
2. **Android test-strategy reference** — the same codebase demonstrates **two complementary testing approaches** that agents should not conflate:

| Strategy | Source set | Runner | What it validates |
|----------|------------|--------|-------------------|
| **Roborazzi (JVM)** | `app/src/test/` | Robolectric on JVM | Compose UI pixels, themes, scroll positions, navigation flows |
| **Instrumented E2E** | `app/src/androidTest/` | Emulator + live API | Voice session, tool execution, turn order, navigation via voice |

Use Roborazzi for visual regression. Use instrumented E2E for live agent behavior. Do not move voice-session tests into `src/test` or screenshot tests into `src/androidTest`.

## Test strategy at a glance

| | Roborazzi | Voice E2E |
|---|-----------|-----------|
| Speed | Seconds | 2–5 minutes |
| Network / API key | Not required | Requires `XAI_API_KEY` |
| Emulator | Not required | Required |
| Flakiness | Low | Higher (live API, timing) |
| CI trigger | Every push + PR | PRs only |
| CI runner | `macos-latest` | `ubuntu-latest` + KVM emulator |
| Primary artifacts | `app/src/screenshots/*.webp` | Test XML + logcat (`VoiceE2E` tag) |

## Read this first

| If you are changing… | Read |
|---------------------|------|
| App structure, navigation, semantics registry | [docs/architecture.md](docs/architecture.md) |
| Voice session, tools, audio, emulator vs device | [docs/voice-assistant.md](docs/voice-assistant.md) |
| Screenshot tests, golden images, Roborazzi setup | [docs/screenshot-testing.md](docs/screenshot-testing.md) |
| Voice E2E test, UiAutomator robot, semantics contract | [docs/voice-e2e-testing.md](docs/voice-e2e-testing.md) |
| Build, CI workflows, scripts, source sets | [docs/ci-and-development.md](docs/ci-and-development.md) |

## Key entry points

```
MainActivity
  └── VoiceAssistantRoot          # composition root (manual DI)
        ├── AppNavHost            # NavHost + TrackScreenContent per route
        └── VoiceOverlayChrome    # connect toggle, transcript, status

GrokVoiceSession                  # WebSocket, PCM audio, turn gating
  └── VoiceToolExecutor           # client-side tool dispatch

RoborazziComposeTest              # JVM screenshot base class
VoiceAppIntegrationTest           # single live voice E2E test
```

## Package map

| Package / path | Responsibility |
|----------------|----------------|
| `ui/` | Compose screens, voice overlay chrome, futuristic theme |
| `navigation/` | `NavRoutes`, `VoiceNavigationHandler` |
| `semantics/` | `TrackScreenContent`, `ScreenContentRegistry` — UI tree for `describe_screen` |
| `voice/` | `GrokVoiceSession`, audio capture/playback, tool definitions, device hints |
| `viewmodel/` | `VoiceAssistantViewModel`, `ItemListScrollController`, `VoiceUiState` |
| `model/` | `Item` data (25 sample items) |
| `src/test/` | Roborazzi screenshot tests + JVM unit tests |
| `src/androidTest/voice/` | Voice E2E integration test + UiAutomator robot |
| `src/debug/voice/` | Debug broadcasts (`VOICE_SPOKEN`, etc.) — debug builds only |
| `src/screenshots/` | Committed WebP golden images |

## Agent conventions

- **Manual DI** in `VoiceAssistantRoot` — no Hilt; wire dependencies with `remember` + `ViewModel.Factory`.
- **Shared controllers** — `NavHostController` and `ItemListScrollController` are shared between UI and voice tools.
- **`TrackScreenContent`** — every `NavHost` destination must register screen elements for `describe_screen`.
- **`GoldenImages.kt`** — all Roborazzi screenshot names live here as constants; never hardcode filenames in tests.
- **Disconnected overlay in Roborazzi** — `setThemedContent()` always renders `VoiceUiState.RoborazziDisconnected` so goldens are stable without a live session.
- **E2E semantics contract** — UiAutomator relies on `contentDescription` strings (`voice-status-*`, `voice-transcript-summary-*`, `item-list-screen`, `item-row-selected-N`). Changing these breaks `VoiceAppTestRobot`.
- **API key at compile time** — `BuildConfig.XAI_API_KEY` is baked in from the `XAI_API_KEY` env var during build; placeholder `no-api-key` causes E2E to fail fast.

## Command cheat sheet

```bash
# Install debug app
./gradlew :app:installDebug

# Roborazzi — verify against committed goldens
./gradlew :app:testDebugUnitTest

# Roborazzi — record new goldens (commit resulting .webp files)
./gradlew :app:testDebugUnitTest \
  -Proborazzi.test.verify=false \
  -Proborazzi.test.record=true

# Open Roborazzi HTML report
open app/build/reports/roborazzi/debug/index.html

# Voice E2E (requires XAI_API_KEY, emulator/device, network)
export XAI_API_KEY=your-key-here
adb shell am force-stop com.example.roborazzidemo
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.example.roborazzidemo.voice.VoiceAppIntegrationTest
```

## Pitfalls

- **Do not mix test layers** — Roborazzi tests belong in `src/test`; live voice tests belong in `src/androidTest`.
- **Emulator half-duplex** — on AVDs the mic is muted while Grok speaks; no barge-in. Wait for `Listening — ask a question` before injecting speech in E2E.
- **Physical device full-duplex** — hardware AEC enables always-on mic and barge-in; behavior differs from emulator.
- **Intentional UI changes** — update goldens with record mode and commit `app/src/screenshots/*.webp` alongside code.
- **New voice tools** — update `VoiceToolDefinitions`, `VoiceToolExecutor`, session instructions, and E2E assertions; add Roborazzi coverage only if UI changes.

## Documentation index

| Doc | Contents |
|-----|----------|
| [docs/architecture.md](docs/architecture.md) | Composition root, navigation graph, semantics registry, dual-test harness |
| [docs/voice-assistant.md](docs/voice-assistant.md) | Grok session, tools, audio duplex, debug broadcasts |
| [docs/screenshot-testing.md](docs/screenshot-testing.md) | Roborazzi composable strategy, goldens, coverage |
| [docs/voice-e2e-testing.md](docs/voice-e2e-testing.md) | Instrumented E2E strategy, robot, semantics contract |
| [docs/ci-and-development.md](docs/ci-and-development.md) | Build setup, CI per strategy, scripts, change checklists |

For user-facing quickstart (install, run, voice setup), see [README.md](README.md).