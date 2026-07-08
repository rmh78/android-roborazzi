#!/usr/bin/env bash
set -euo pipefail

TEST_PACKAGE="com.example.roborazzidemo.voice"
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
  "-Pandroid.testInstrumentationRunnerArguments.package=${TEST_PACKAGE}"
)

if [[ -z "${XAI_API_KEY:-}" ]]; then
  echo "::error::XAI_API_KEY must be set for voice integration test"
  exit 1
fi

log "Running voice integration test"
bash scripts/verify-buildconfig-api-key.sh
start_logcat
./gradlew "${GRADLE_ARGS[@]}"

RESULTS_DIR="app/build/outputs/androidTest-results/connected/debug"
if [[ -d "$RESULTS_DIR" ]]; then
  if grep -hE 'testsuite[^>]*skipped="[1-9]' "$RESULTS_DIR"/*.xml 2>/dev/null | grep -q .; then
    echo "::error::Voice integration test was skipped — live API path did not run"
    exit 1
  fi
fi

log "Voice integration test passed (0 failures, 0 skipped)"