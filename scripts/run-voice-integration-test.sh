#!/usr/bin/env bash
set -euo pipefail

TEST_CLASS="com.example.roborazzidemo.voice.VoiceAppIntegrationTest"
GRADLE_ARGS=(
  :app:connectedDebugAndroidTest
  --stacktrace
  "-Pandroid.testInstrumentationRunnerArguments.class=${TEST_CLASS}"
)

for attempt in 1 2; do
  echo "Voice integration test attempt ${attempt}/2"
  if ./gradlew "${GRADLE_ARGS[@]}"; then
    exit 0
  fi
  adb shell am force-stop com.example.roborazzidemo || true
  sleep 15
done

exit 1