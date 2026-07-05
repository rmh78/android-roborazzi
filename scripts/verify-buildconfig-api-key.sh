#!/usr/bin/env bash
# Verifies the generated debug BuildConfig was compiled with a live XAI_API_KEY.
# Never prints the secret value.
set -euo pipefail

BUILDCONFIG="app/build/generated/source/buildConfig/debug/com/example/roborazzidemo/BuildConfig.java"

if [[ -z "${XAI_API_KEY:-}" ]]; then
  echo "::error::XAI_API_KEY environment variable is not set"
  exit 1
fi

if [[ ! -f "$BUILDCONFIG" ]]; then
  echo "::error::Debug BuildConfig not found at $BUILDCONFIG — run assembleDebug first"
  exit 1
fi

if grep -q 'no-api-key' "$BUILDCONFIG"; then
  echo "::error::Debug APK BuildConfig still contains placeholder no-api-key"
  exit 1
fi

if ! grep -q 'XAI_API_KEY' "$BUILDCONFIG"; then
  echo "::error::Debug BuildConfig missing XAI_API_KEY field"
  exit 1
fi

echo "Verified debug BuildConfig contains live XAI_API_KEY (value redacted)"