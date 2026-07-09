#!/usr/bin/env bash
# Voice-friendly AVD: API 34 google_apis (stable ranchu mic). Avoid API 37+ ps16k images.
set -euo pipefail

AVD_NAME="Voice_API_34"
ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
export PATH="$ANDROID_HOME/emulator:$ANDROID_HOME/platform-tools:$PATH"

if ! emulator -list-avds | grep -qx "$AVD_NAME"; then
  echo "AVD '$AVD_NAME' not found. Create it with:" >&2
  echo "  avdmanager create avd -n $AVD_NAME -k 'system-images;android-34;google_apis;arm64-v8a' -d pixel_5" >&2
  exit 1
fi

echo "Starting $AVD_NAME (API 34, minimal footprint)…"
emulator -avd "$AVD_NAME" -no-snapshot-load -gpu auto &
EMU_PID=$!

echo "Waiting for device…"
adb wait-for-device
while [[ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" != "1" ]]; do
  sleep 2
done

adb emu avd hostmicon
echo "Ready. Host microphone enabled. Emulator PID=$EMU_PID"