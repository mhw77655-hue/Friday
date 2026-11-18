#!/usr/bin/env bash
set -euo pipefail

EVIDENCE_DIR="${RUNNER_TEMP}/friday-emulator"
mkdir -p "$EVIDENCE_DIR"
timeout 20s adb logcat -c

capture_boot_diagnostics() {
  {
    echo "=== sys.boot_completed ==="
    adb shell getprop sys.boot_completed 2>&1 || true
    echo "=== package service ==="
    adb shell service check package 2>&1 || true
    echo "=== PackageManager package enumeration ==="
    adb shell pm list packages 2>&1 || true
    echo "=== Settings provider probe ==="
    adb shell settings get global device_provisioned 2>&1 || true
    echo "=== Storage volume state ==="
    adb shell sm list-volumes all 2>&1 || true
    echo "=== Mount service diagnostics ==="
    adb shell dumpsys mount 2>&1 || true
    echo "=== Settings provider registration ==="
    adb shell dumpsys activity providers 2>&1 || true
    echo "=== PackageInstaller service ==="
    adb shell dumpsys package 2>&1 | grep -i -C 3 'PackageInstaller' || true
    echo "=== system_server and provider boot log ==="
    adb logcat -b all -d -t 16000 2>&1 || true
  } > "$EVIDENCE_DIR/boot-readiness-diagnostics.txt"
}

trap 'adb logcat -d -t 16000 > "$EVIDENCE_DIR/logcat.txt" 2>/dev/null || true; adb shell dumpsys activity services > "$EVIDENCE_DIR/services.txt" 2>/dev/null || true; adb shell dumpsys package com.jarvis.app > "$EVIDENCE_DIR/package.txt" 2>/dev/null || true' EXIT

# Package enumeration may succeed before SettingsProvider is registered.
# PackageInstaller queries Settings during session creation, so the helper polls
# the actual provider and has unit tests for the early-boot false-positive state.
if ! python3 tools/friday-builder/wait_for_android_system.py \
  --timeout-seconds 600 \
  --interval-seconds 5 \
  --evidence-file "$EVIDENCE_DIR/boot-readiness-diagnostics.txt"; then
  capture_boot_diagnostics
  cat "$EVIDENCE_DIR/boot-readiness-diagnostics.txt" >&2
  exit 20
fi

# Keep install failure distinct from boot-readiness failure and retain the exact
# provider/system evidence needed to diagnose a PackageInstaller regression.
if ! python3 tools/friday-builder/install_apk_with_retry.py \
  mobile/app/build/outputs/apk/debug/app-debug.apk \
  --max-attempts 24 \
  --retry-interval-seconds 5; then
  echo "ANDROID_INSTALL_FAILED: PackageInstaller rejected the APK after bounded transient-race retries." >&2
  capture_boot_diagnostics
  cat "$EVIDENCE_DIR/boot-readiness-diagnostics.txt" >&2
  exit 21
fi

python3 tools/friday-builder/apply_device_state_capsule.py .github/android-emulator/default-state-capsule.json
./gradlew :mobile:app:connectedDebugAndroidTest
timeout 30s adb shell am start -n com.jarvis.app/.MainActivity || true
sleep 8
adb shell dumpsys activity top > "$EVIDENCE_DIR/activity-top.txt" 2>/dev/null || true
adb exec-out screencap -p > "$EVIDENCE_DIR/screen.png" || true
