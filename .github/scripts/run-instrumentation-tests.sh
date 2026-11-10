#!/usr/bin/env bash
set -euo pipefail

# Wait for the emulator to be available before changing verifier settings.
adb wait-for-device

for attempt in 1 2 3 4 5; do
  if adb shell settings put global package_verifier_enable 0 \
      && adb shell settings put global verifier_verify_adb_installs 0; then
    break
  fi

  if [[ "$attempt" -eq 5 ]]; then
    echo "Unable to update emulator package verifier settings after 5 attempts." >&2
    exit 1
  fi

  adb wait-for-device || true
  sleep 5
done

gradle connectedDebugAndroidTest --no-daemon --no-parallel -Pkotlin.compiler.execution.strategy=daemon
