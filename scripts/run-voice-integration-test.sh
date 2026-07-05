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

for attempt in 1 2 3; do
  log "Voice integration test attempt ${attempt}/3"
  start_logcat
  if ./gradlew "${GRADLE_ARGS[@]}"; then
    log "Voice integration test passed on attempt ${attempt}"
    exit 0
  fi
  stop_logcat
  log "Attempt ${attempt} failed — restarting app before retry"
  adb shell am force-stop com.example.roborazzidemo || true
  sleep 15
done

log "Voice integration test failed after 3 attempts"
exit 1