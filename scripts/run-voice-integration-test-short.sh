#!/usr/bin/env bash
# Fast voice E2E smoke: integration test only, 2 user turns (~1 min).
set -euo pipefail

export VOICE_E2E_SHORT=true
export VOICE_E2E_CLASS="${VOICE_E2E_CLASS:-com.example.roborazzidemo.voice.VoiceAppIntegrationTest}"

exec bash "$(dirname "$0")/run-voice-integration-test.sh"