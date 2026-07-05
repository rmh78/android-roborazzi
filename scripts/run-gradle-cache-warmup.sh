#!/usr/bin/env bash
set -euo pipefail

log() {
  echo "[$(date -u +%H:%M:%S)] $*"
}

if [[ -z "${XAI_API_KEY:-}" ]]; then
  echo "::error::XAI_API_KEY must be set for Gradle cache warmup"
  exit 1
fi

log "Warming Gradle cache (assemble debug app + androidTest APKs, no emulator)"
./gradlew \
  :app:assembleDebug \
  :app:assembleDebugAndroidTest \
  --console=plain \
  --stacktrace
bash scripts/verify-buildconfig-api-key.sh
log "Gradle cache warmup passed"