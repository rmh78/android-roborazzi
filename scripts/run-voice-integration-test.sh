#!/usr/bin/env bash
set -euo pipefail

TEST_CLASS="com.example.roborazzidemo.voice.VoiceAppIntegrationTest"
LOGCAT_PID=""

log() {
  echo "[$(date -u +%H:%M:%S)] $*"
}

start_logcat() {
  adb logcat -c >/dev/null 2>&1 || true
  log "Streaming emulator logs (tag VoiceE2E, TestRunner) — test steps appear below"
  adb logcat -v time VoiceE2E:I TestRunner:I AndroidJUnitRunner:I Instrumentation:I *:S &
  LOGCAT_PID=$!
}

stop_logcat() {
  if [[ -n "${LOGCAT_PID}" ]]; then
    kill "${LOGCAT_PID}" >/dev/null 2>&1 || true
    wait "${LOGCAT_PID}" 2>/dev/null || true
    LOGCAT_PID=""
  fi
}

trap stop_logcat EXIT

GRADLE_ARGS=(
  :app:connectedDebugAndroidTest
  --console=plain
  --stacktrace
  "-Pandroid.testInstrumentationRunnerArguments.class=${TEST_CLASS}"
)

log "Running voice integration test"
start_logcat
./gradlew "${GRADLE_ARGS[@]}"
log "Voice integration test passed"