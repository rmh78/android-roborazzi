# Voice E2E testing (instrumented emulator strategy)

The **integration/agent-behavior layer** — validates live voice session behavior on a real Android runtime with the xAI API.

## When to use instrumented E2E

- Live WebSocket session lifecycle (connect, greet, disconnect)
- Tool execution (`navigate_to_screen`, `open_list_item`, `describe_screen`, `web_search`)
- Conversation turn order (You → Grok pairs)
- Navigation driven by voice commands
- Audio/session timing and emulator half-duplex behavior

Do **not** use E2E for pixel-level UI regression — that belongs in [screenshot-testing.md](screenshot-testing.md).

## Why emulator + instrumented

| Requirement | Why JVM/Roborazzi is insufficient |
|-------------|-----------------------------------|
| WebSocket to xAI | Needs real network stack |
| `RECORD_AUDIO` permission | Needs Android runtime |
| Tool dispatch through live session | Needs `GrokVoiceSession` + ViewModel lifecycle |
| UiAutomator semantics | Needs rendered activity on device |
| Turn-order validation | Needs real API responses |

## Test entry point

Single integration test: [`VoiceAppIntegrationTest.kt`](../app/src/androidTest/java/com/example/roborazzidemo/voice/VoiceAppIntegrationTest.kt)

- Runs via `ActivityScenarioRule(MainActivity::class.java)`
- Grants `RECORD_AUDIO` and `MODIFY_AUDIO_SETTINGS`
- Fails fast if `BuildConfig.XAI_API_KEY == "no-api-key"`
- Typical runtime: 2–5 minutes

### Test flow (11 user turns)

1. Connect → wait for voice ready → optional Grok greeting
2. `describe_screen` on home
3. `web_search` — weather in Munich
4. `navigate_to_screen` → items list
5. `open_list_item` — scroll to item 10
6. `describe_screen` on list
7. `navigate_to_screen` → item 1 detail
8. `describe_screen` on detail
9. `navigate_to_screen` → items list (back from detail)
10. `navigate_to_screen` → item 999 (not-found)
11. `navigate_to_screen` → items list, then home
12. Assert turn counts (min 11 You, min 10 Grok) and valid turn order
13. Disconnect

## Robot harness

[`VoiceAppTestRobot.kt`](../app/src/androidTest/java/com/example/roborazzidemo/voice/support/VoiceAppTestRobot.kt) wraps UiAutomator with voice-specific sync:

| Method | Purpose |
|--------|---------|
| `connect()` / `disconnect()` | Toggle voice connect switch |
| `waitForVoiceReady()` | Session connected + listening phase |
| `waitForReadyToSpeak()` | `Listening — ask a question` status |
| `speakAndWaitForTool(text, toolName)` | Inject speech, wait for tool invocation |
| `speakAndWaitForResponse(text)` | Inject speech, wait for Grok reply |
| `waitForItemsListScreen()` | `item-list-screen` semantics or "Items" text |
| `waitForListItemSelected(index)` | `item-row-selected-N` semantics |
| `assertExchangeTurns()` | You → Grok pair completed |
| `assertValidConversationTurns()` | Full turn-order validation |
| `assertConversationTurnCounts(minYou, minGrok)` | Minimum turn counts |

Logging uses tag `VoiceE2E` via [`VoiceE2ELog.kt`](../app/src/androidTest/java/com/example/roborazzidemo/voice/support/VoiceE2ELog.kt). CI streams this via logcat in [`scripts/run-voice-integration-test.sh`](../scripts/run-voice-integration-test.sh).

## Speech simulation (TTS + inject)

E2E does not rely on host microphone input. [`TestSpeechAnnouncer.kt`](../app/src/androidTest/java/com/example/roborazzidemo/voice/support/TestSpeechAnnouncer.kt) uses:

1. Optional emulator TTS playback (audible on AVD)
2. `VOICE_SPOKEN` debug broadcast to inject the user turn into the live session

This avoids host-mic flakiness in CI. Disable TTS playback with:

```bash
-Pandroid.testInstrumentationRunnerArguments.disableTestSpeechPlayback=true
```

## Semantics contract

UiAutomator relies on `contentDescription` strings set in Compose. **Changing these breaks the robot.**

### Voice overlay ([`VoiceTranscriptOverlay.kt`](../app/src/main/java/com/example/roborazzidemo/ui/VoiceTranscriptOverlay.kt))

| Semantics ID | Pattern | Used for |
|--------------|---------|----------|
| Overlay root | `voice-assistant-overlay` | Chrome visibility |
| Connect switch | `voice-connect-switch` | Connect/disconnect |
| Status line | `voice-status-{status text}` | Ready-to-speak sync (`Listening — ask a question`) |
| Turn phase | `voice-turn-phase-{assistant\|user\|listening}` | Turn state |
| Mic level | `voice-mic-level` | Connected chrome check |
| Last tool | `voice-last-tool-{toolName}` | Tool assertion |
| Transcript | `voice-transcript-summary-{summary}` | Turn order validation |
| Transcript turns | `voice-transcript-turn-{index}-{you\|grok}` | Individual turns |

### Screen semantics

| Semantics ID | Set in | Used for |
|--------------|--------|----------|
| `item-list-screen` | `ItemListScreen.kt` | List screen detection |
| `item-row-{N}` | `ItemListScreen.kt` | List row (not highlighted) |
| `item-row-selected-{N}` | `ItemListScreen.kt` | Highlighted row after `open_list_item` |
| `voice-sig-level-{label}` | `FuturisticComponents.kt` | Mic level UI fallback |

Screen content is also detected by visible text: `"Roborazzi Demo"` (home), `"Items"` (list), `"Item not found"` (not-found), item titles (detail).

## Turn-order validation

The robot validates conversation integrity:

- Optional Grok greeting as first turn (if present)
- Subsequent turns alternate You → Grok
- `hasEchoTurnAfterGrok` detects echo loops (stale user turns after Grok on emulator)
- Status line (`voice-status-*`) is authoritative over turn-indicator nodes

## Local run

```bash
export XAI_API_KEY=your-key-here
adb shell am force-stop com.example.roborazzidemo

./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.example.roborazzidemo.voice.VoiceAppIntegrationTest
```

The API key must be set **before building** — it is compiled into `BuildConfig.XAI_API_KEY`.

## CI

[`.github/workflows/voice-integration-test.yml`](../.github/workflows/voice-integration-test.yml):

- Trigger: PRs only
- Requires `XAI_API_KEY` repository secret (fails fast if missing)
- Runner: `ubuntu-latest` with KVM
- Emulator: API 34, Pixel 6, x86_64
- Flow: cache warmup → emulator boot → `scripts/run-voice-integration-test.sh`
- On failure: uploads `voice-integration-test-results` artifact

## Checklist: extend E2E coverage

1. Add a new step in `VoiceAppIntegrationTest` with `VoiceE2ELog.step(...)`.
2. Use `speakAndWaitForTool` or `speakAndWaitForResponse` with natural language prompts.
3. Add robot wait helpers if a new screen needs detection (semantics or text).
4. Call `assertExchangeTurns()` after each exchange.
5. If adding a new tool: ensure semantics for `voice-last-tool-{name}` are set in `VoiceTranscriptOverlay`.
6. Update minimum turn counts in `assertConversationTurnCounts` if adding turns.
7. Run locally on emulator with live API key before pushing.
8. Verify CI secret is configured for PR runs.