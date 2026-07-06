#!/usr/bin/env bash
# Records the emulator screen while VoiceAppIntegrationTest runs, then pulls an MP4
# suitable for embedding in README.md.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

OUTPUT_DIR="docs"
OUTPUT_FILE="${OUTPUT_DIR}/voice-integration-test.mp4"
DEVICE_PATH="/sdcard/voice-integration-test.mp4"
TEST_CLASS="com.example.roborazzidemo.voice.VoiceAppIntegrationTest"
# Test runtime is ~2–3 minutes; screenrecord caps at 180s on most API levels.
RECORD_LIMIT_SEC=180
BIT_RATE=2000000

log() {
  echo "[$(date -u +%H:%M:%S)] $*"
}

require_device() {
  if ! adb get-state >/dev/null 2>&1; then
    echo "::error::No adb device — start an emulator or connect a device"
    exit 1
  fi
}

require_api_key() {
  if [[ -z "${XAI_API_KEY:-}" ]]; then
    echo "::error::XAI_API_KEY must be set"
    exit 1
  fi
  bash scripts/verify-buildconfig-api-key.sh
}

stop_screenrecord() {
  if [[ -n "${RECORD_PID:-}" ]]; then
    kill -INT "${RECORD_PID}" 2>/dev/null || true
    wait "${RECORD_PID}" 2>/dev/null || true
    RECORD_PID=""
    sleep 2
  fi
}

pull_recording() {
  if [[ -f "${OUTPUT_FILE}" && -s "${OUTPUT_FILE}" ]]; then
    return 0
  fi
  if adb shell test -f "${DEVICE_PATH}" 2>/dev/null; then
    log "Pulling recording to ${OUTPUT_FILE}"
    adb pull "${DEVICE_PATH}" "${OUTPUT_FILE}" >/dev/null
    adb shell rm -f "${DEVICE_PATH}" >/dev/null 2>&1 || true
    if [[ -s "${OUTPUT_FILE}" ]]; then
      BYTES=$(wc -c < "${OUTPUT_FILE}" | tr -d ' ')
      log "Recording saved (${BYTES} bytes) → ${OUTPUT_FILE}"
      return 0
    fi
  fi
  echo "::error::Recording is empty — screenrecord may have failed"
  return 1
}

cleanup() {
  stop_screenrecord
  pull_recording || true
}

trap cleanup EXIT

require_device
require_api_key
mkdir -p "${OUTPUT_DIR}"

log "Installing debug APKs"
./gradlew :app:installDebug :app:installDebugAndroidTest --console=plain -q

log "Waking emulator and granting runtime permissions"
adb shell input keyevent KEYCODE_WAKEUP >/dev/null 2>&1 || true
adb shell pm grant com.example.roborazzidemo android.permission.RECORD_AUDIO >/dev/null 2>&1 || true
adb shell pm grant com.example.roborazzidemo android.permission.MODIFY_AUDIO_SETTINGS >/dev/null 2>&1 || true

log "Starting screen recording (${RECORD_LIMIT_SEC}s max, bit-rate=${BIT_RATE})"
adb shell rm -f "${DEVICE_PATH}" >/dev/null 2>&1 || true
adb shell screenrecord --bit-rate "${BIT_RATE}" --time-limit "${RECORD_LIMIT_SEC}" "${DEVICE_PATH}" &
RECORD_PID=$!

log "Running voice integration test"
set +e
./gradlew :app:connectedDebugAndroidTest \
  --console=plain \
  "-Pandroid.testInstrumentationRunnerArguments.class=${TEST_CLASS}"
TEST_EXIT=$?
set -e

trap - EXIT
stop_screenrecord
pull_recording

if [[ "${TEST_EXIT}" -ne 0 ]]; then
  echo "::error::Integration test failed (exit ${TEST_EXIT}) — video saved to ${OUTPUT_FILE}"
  exit "${TEST_EXIT}"
fi

log "Integration test and recording completed successfully"