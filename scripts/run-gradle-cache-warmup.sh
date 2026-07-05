#!/usr/bin/env bash
set -euo pipefail

log() {
  echo "[$(date -u +%H:%M:%S)] $*"
}

log "Warming Gradle cache (assemble debug app + androidTest APKs, no emulator)"
./gradlew \
  :app:assembleDebug \
  :app:assembleDebugAndroidTest \
  --console=plain \
  --stacktrace
log "Gradle cache warmup passed"