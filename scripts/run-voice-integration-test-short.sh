#!/usr/bin/env bash
# Fast voice E2E smoke: integration test only, 2 user turns (~1 min).
# Audible user prompts: add -Pandroid.testInstrumentationRunnerArguments.voiceE2eAudiblePrompts=true
# to the gradle invocation (see docs/voice-e2e-testing.md).
set -euo pipefail

export VOICE_E2E_SHORT=true
export VOICE_E2E_CLASS="${VOICE_E2E_CLASS:-com.example.roborazzidemo.voice.VoiceAppIntegrationTest}"

exec bash "$(dirname "$0")/run-voice-integration-test.sh"