#!/usr/bin/env bash
# Records the voice integration E2E test as an MP4 with emulator audio (TTS + Grok playback).
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUTPUT="${ROOT_DIR}/docs/voice-integration-test.mp4"
LOCK_FILE="${ROOT_DIR}/.voice-integration-test-recording.lock"
SCRCPY_PID=""
AUDIO_SOURCE="${AUDIO_SOURCE:-output}"
MIN_AUDIO_DB="${MIN_AUDIO_DB:--60}"

export ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
export PATH="$ANDROID_HOME/emulator:$ANDROID_HOME/platform-tools:/opt/homebrew/bin:$PATH"

log() {
  echo "[$(date -u +%H:%M:%S)] $*"
}

cleanup() {
  if [[ -n "${SCRCPY_PID}" ]]; then
    log "Stopping screen recording (pid ${SCRCPY_PID})"
    kill -INT "${SCRCPY_PID}" 2>/dev/null || true
    # Allow scrcpy/ffmpeg to flush the MP4 moov atom before we validate the file.
    for _ in $(seq 1 30); do
      if ! kill -0 "${SCRCPY_PID}" 2>/dev/null; then
        break
      fi
      sleep 1
    done
    wait "${SCRCPY_PID}" 2>/dev/null || true
    SCRCPY_PID=""
    sleep 2
  fi
}
trap cleanup EXIT

if [[ -f "${LOCK_FILE}" ]]; then
  echo "::error::Another recording is in progress (lock: ${LOCK_FILE})"
  exit 1
fi
echo $$ >"${LOCK_FILE}"
trap 'rm -f "${LOCK_FILE}"; cleanup' EXIT

if pgrep -x scrcpy >/dev/null 2>&1; then
  echo "::error::scrcpy is already running — stop it before recording"
  exit 1
fi

if [[ -z "${XAI_API_KEY:-}" ]]; then
  echo "::error::XAI_API_KEY must be set before recording"
  exit 1
fi

if ! adb devices | grep -qE 'emulator|device'; then
  echo "::error::No emulator or device connected"
  exit 1
fi

mkdir -p "$(dirname "${OUTPUT}")"
rm -f "${OUTPUT}"

log "Installing debug app with live API key"
bash "${ROOT_DIR}/scripts/verify-buildconfig-api-key.sh"
"${ROOT_DIR}/gradlew" :app:installDebug --console=plain -q

adb shell am force-stop com.example.roborazzidemo

log "Starting scrcpy recording → ${OUTPUT} (audio-source=${AUDIO_SOURCE})"
scrcpy \
  --no-window \
  --no-playback \
  --max-size=1280 \
  --video-bit-rate=4M \
  --audio-bit-rate=128K \
  --audio-source="${AUDIO_SOURCE}" \
  --record="${OUTPUT}" &
SCRCPY_PID=$!
sleep 3

if ! kill -0 "${SCRCPY_PID}" 2>/dev/null; then
  echo "::error::scrcpy failed to start"
  exit 1
fi

log "Running voice integration test (typical runtime 2–5 minutes)"
bash "${ROOT_DIR}/scripts/run-voice-integration-test.sh"

cleanup
rm -f "${LOCK_FILE}"
trap - EXIT

log "Recording complete"
if [[ ! -s "${OUTPUT}" ]]; then
  echo "::error::No video file produced at ${OUTPUT}"
  exit 1
fi

if ! ffprobe -v error -select_streams v:0 -show_entries stream=codec_name -of csv=p=0 "${OUTPUT}" >/dev/null 2>&1; then
  echo "::error::Recorded video is not a valid MP4 (scrcpy may not have finalized)"
  exit 1
fi

AUDIO_MAX_DB="$(ffmpeg -hide_banner -i "${OUTPUT}" -af volumedetect -f null - 2>&1 | awk -F': ' '/max_volume/ {print $2}' | tr -d ' dB' || true)"
if [[ -z "${AUDIO_MAX_DB}" ]] || awk "BEGIN {exit !(${AUDIO_MAX_DB} < ${MIN_AUDIO_DB})}"; then
  echo "::error::Recorded audio is too quiet (max_volume=${AUDIO_MAX_DB:-none} dB, need > ${MIN_AUDIO_DB} dB)"
  exit 1
fi

ls -lh "${OUTPUT}"
ffprobe -hide_banner "${OUTPUT}" 2>&1 | head -8
log "Audio max volume: ${AUDIO_MAX_DB} dB"