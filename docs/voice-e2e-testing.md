# Voice E2E testing (instrumented emulator strategy)

The **integration/agent-behavior layer** — validates live voice session behavior on a real Android runtime with the xAI API.

## When to use instrumented E2E

- Live WebSocket session lifecycle (connect, greet, disconnect)
- Tool execution (`navigate_to_screen`, `open_list_item`, `describe_screen`, `web_search`)
- Conversation turn order (You → Grok pairs)
- Navigation driven by voice commands
- Session timing after assistant playback

Do **not** use E2E for pixel-level UI regression — that belongs in [screenshot-testing.md](screenshot-testing.md).
Do **not** use E2E to validate live microphone DSP — use manual device tests for that.

## Why inject-only (stable / low CPU)

AVD user-audio simulation (TTS, continuous `AudioRecord`, mute thrash) spikes CPU and is flaky.
The harness therefore runs in **inject-only** mode by default:

| Concern | Strategy |
|---------|----------|
| User turns | `VOICE_SPOKEN` text inject (no TTS, no beep) |
| Microphone | **Not opened** during the test (`skipLiveCapture`) |
| Assistant audio | Real PCM playback from the API (still validated) |
| Ready-to-speak | Status `Listening — ask a question` after greeting/response drain |

This exercises tools, navigation, transcripts, and turn order without host-mic or speech-synthesis load.

## Why emulator + instrumented

| Requirement | Why JVM/Roborazzi is insufficient |
|-------------|-----------------------------------|
| WebSocket to xAI | Needs real network stack |
| Tool dispatch through live session | Needs `GrokVoiceSession` + ViewModel lifecycle |
| UiAutomator semantics | Needs rendered activity on device |
| Turn-order validation | Needs real API responses |

## Test entry point

Single integration test: [`VoiceAppIntegrationTest.kt`](../app/src/androidTest/java/com/example/roborazzidemo/voice/VoiceAppIntegrationTest.kt)

- Runs via `ActivityScenarioRule(MainActivity::class.java)`
- Grants `RECORD_AUDIO` and `MODIFY_AUDIO_SETTINGS` (permissions still required by the app)
- Enables E2E inject-only via `VOICE_E2E_MODE` before connect
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
| `waitForVoiceReady()` | Session connected + post-Hello **Listening** |
| `waitForReadyToSpeak()` | Strict `Listening` status only |
| `speakAndWaitForTool(text, toolName)` | Inject turn, wait for tool + follow-up |
| `speakAndWaitForResponse(text)` | Inject turn, wait for Grok reply |
| `waitForItemsListScreen()` | `item-list-screen` semantics or "Items" text |
| `waitForListItemSelected(index)` | `item-row-selected-N` semantics |
| `assertExchangeTurns()` | You → Grok pair completed |
| `assertValidConversationTurns()` | Full turn-order validation |
| `assertConversationTurnCounts(minYou, minGrok)` | Minimum turn counts |

Logging uses tag `VoiceE2E` via [`VoiceE2ELog.kt`](../app/src/androidTest/java/com/example/roborazzidemo/voice/support/VoiceE2ELog.kt). CI streams this via logcat in [`scripts/run-voice-integration-test.sh`](../scripts/run-voice-integration-test.sh).

## User turns (inject only)

[`TestSpeechAnnouncer.kt`](../app/src/androidTest/java/com/example/roborazzidemo/voice/support/TestSpeechAnnouncer.kt):

1. Enables `VOICE_E2E_MODE` (`inject_only` + `skip_live_capture`) before the session starts
2. On each step: `VOICE_SPOKEN` inject only (retried while the user-turn gate is closed)

Optional audible cues (not recommended for CI):

```bash
# Short system beep per user turn
-Pandroid.testInstrumentationRunnerArguments.testSpeechMode=beep

# Full TextToSpeech of the prompt (high AVD CPU — demo only)
-Pandroid.testInstrumentationRunnerArguments.testSpeechMode=tts
```

## Semantics contract (do not break casually)

| Element | `contentDescription` | Used for |
|---------|---------------------|----------|
| Status line | `voice-status-{status text}` | Ready-to-speak sync (`Listening — ask a question`) |
| Transcript summary | `voice-transcript-summary-you,grok,...` | Turn order |
| Last tool | `voice-last-tool-{name}` | Tool assertions |
| Connect switch | `voice-connect-switch` | Connect/disconnect |
| Overlay | `voice-assistant-overlay` | Shell visibility |
| List screen | `item-list-screen` | Navigation |
| Selected row | `item-row-selected-N` | `open_list_item` |

## Run locally

```bash
export XAI_API_KEY=your-key-here
adb shell am force-stop com.example.roborazzidemo
bash scripts/run-voice-integration-test.sh
```
