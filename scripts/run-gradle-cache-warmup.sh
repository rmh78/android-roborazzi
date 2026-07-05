#!/usr/bin/env bash
set -euo pipefail

TEST_CLASS="com.example.roborazzidemo.ci.GradleCacheWarmupTest"

log() {
  echo "[$(date -u +%H:%M:%S)] $*"
}

GRADLE_ARGS=(
  :app:connectedDebugAndroidTest
  --console=plain
  --stacktrace
  "-Pandroid.testInstrumentationRunnerArguments.class=${TEST_CLASS}"
)

log "Running Gradle cache warmup (connected androidTest build + trivial launch test)"
./gradlew "${GRADLE_ARGS[@]}"
log "Gradle cache warmup passed"