#!/usr/bin/env bash
# Enable the AVD virtual microphone to use the host audio input.
# Required for manual voice testing on the emulator (SIG meter + live speech).
set -euo pipefail

ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
export PATH="$ANDROID_HOME/platform-tools:$PATH"

DEVICE="${1:-}"
if [[ -z "$DEVICE" ]]; then
  DEVICE="$(adb devices | awk '/^emulator-/{print $1; exit}')"
fi

if [[ -z "$DEVICE" ]]; then
  echo "No emulator found. Start an AVD first." >&2
  exit 1
fi

echo "Enabling host microphone on $DEVICE …"
adb -s "$DEVICE" emu avd hostmicon
echo "Done. Also check Extended Controls → Microphone → Virtual microphone uses host audio input."