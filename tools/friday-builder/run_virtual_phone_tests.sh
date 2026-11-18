#!/usr/bin/env bash
set -euo pipefail

EVIDENCE_DIR="${RUNNER_TEMP}/friday-emulator"
mkdir -p "$EVIDENCE_DIR"
adb logcat -c
trap 'adb logcat -d -t 16000 > "$EVIDENCE_DIR/logcat.txt" 2>/dev/null || true; adb shell dumpsys activity services > "$EVIDENCE_DIR/services.txt" 2>/dev/null || true; adb shell dumpsys package com.jarvis.app > "$EVIDENCE_DIR/package.txt" 2>/dev/null || true' EXIT

# Emulator boot completion can precede PackageManager readiness. Poll from one
# real Bash process; android-emulator-runner's script input is command-oriented
# and must not be given a multi-line shell control structure directly.
package_ready=0
for attempt in $(seq 1 48); do
  package_list="$(adb shell pm list packages 2>/dev/null || true)"
  if grep -q '^package:android$' <<< "$package_list" &&
     grep -q '^package:com.android.settings$' <<< "$package_list"; then
    package_ready=1
    break
  fi
  echo "Waiting for Android PackageManager ($attempt/48)"
  sleep 5
done
if [[ "$package_ready" != "1" ]]; then
  echo "Android PackageManager never became ready after emulator boot." >&2
  adb logcat -d -t 6000 >&2 || true
  exit 20
fi

adb install -r mobile/app/build/outputs/apk/debug/app-debug.apk
python3 tools/friday-builder/apply_device_state_capsule.py .github/android-emulator/default-state-capsule.json
./gradlew :mobile:app:connectedDebugAndroidTest
adb shell am start -n com.jarvis.app/.MainActivity || true
sleep 8
adb shell dumpsys activity top > "$EVIDENCE_DIR/activity-top.txt" 2>/dev/null || true
adb exec-out screencap -p > "$EVIDENCE_DIR/screen.png" || true
