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

# The instrumentation screenshot sweep captures each user-facing destination
# on this exact virtual device. Pull the app-private evidence directory into
# the run artifact; absence is a failed visual-diagnostics test, not a pass.
UI_AUDIT_DEVICE_DIR="/sdcard/Android/data/com.jarvis.app/files/friday-ui-audit"
mkdir -p "$EVIDENCE_DIR/ui-audit"
if ! adb pull "$UI_AUDIT_DEVICE_DIR" "$EVIDENCE_DIR/ui-audit"; then
  echo "FRIDAY_UI_AUDIT_ARTIFACT_MISSING: instrumentation did not produce app screenshots." >&2
  exit 30
fi
if ! find "$EVIDENCE_DIR/ui-audit" -maxdepth 1 -type f -name '*.png' | grep -q .; then
  echo "FRIDAY_UI_AUDIT_EMPTY: no screen captures were pulled from the device." >&2
  exit 31
fi

# Instrumentation may leave the target app stopped or uninstall it. Reinstall
# the exact debug APK before the real application launch smoke test.
if ! python3 tools/friday-builder/install_apk_with_retry.py mobile/app/build/outputs/apk/debug/app-debug.apk --max-attempts 6 --retry-interval-seconds 5; then
  echo "FRIDAY_APP_REINSTALL_FAILED: target APK was not present after instrumentation." >&2
  exit 22
fi

# The synthetic capsule deliberately begins with microphone access denied.
# Instrumentation validates the denied baseline; the final foreground smoke test
# grants the permission explicitly so Friday can leave Android's permission
# controller and prove that the actual app activity remains resumed.
if ! adb shell pm grant com.jarvis.app android.permission.RECORD_AUDIO; then
  echo "FRIDAY_MIC_PERMISSION_SETUP_FAILED: could not grant the test-only voice permission." >&2
  exit 29
fi

PACKAGE_PATH="$(adb shell pm path com.jarvis.app 2>&1 | tr -d '\r')"
if [[ "$PACKAGE_PATH" != package:* ]]; then
  echo "FRIDAY_PACKAGE_MISSING: PackageManager cannot resolve com.jarvis.app after install." >&2
  printf '%s\n' "$PACKAGE_PATH" >&2
  exit 23
fi

if ! timeout 30s adb shell am start -W -n com.jarvis.app/.MainActivity > "$EVIDENCE_DIR/activity-launch.txt" 2>&1; then
  cat "$EVIDENCE_DIR/activity-launch.txt" >&2
  echo "FRIDAY_ACTIVITY_LAUNCH_FAILED: MainActivity did not start." >&2
  exit 24
fi
if ! grep -q "Status: ok" "$EVIDENCE_DIR/activity-launch.txt"; then
  cat "$EVIDENCE_DIR/activity-launch.txt" >&2
  echo "FRIDAY_ACTIVITY_LAUNCH_FAILED: ActivityManager did not confirm a successful launch." >&2
  exit 25
fi
sleep 8
adb shell dumpsys activity top > "$EVIDENCE_DIR/activity-top.txt"
if ! grep -q "ACTIVITY com.jarvis.app/.MainActivity" "$EVIDENCE_DIR/activity-top.txt"; then
  cat "$EVIDENCE_DIR/activity-top.txt" >&2
  echo "FRIDAY_ACTIVITY_NOT_RESUMED: MainActivity is not the foreground activity." >&2
  exit 26
fi
APP_PID="$(adb shell pidof com.jarvis.app 2>/dev/null | tr -d '\r')"
if [[ -z "$APP_PID" ]]; then
  echo "FRIDAY_PROCESS_MISSING: Friday process exited after launch." >&2
  exit 27
fi
adb exec-out screencap -p > "$EVIDENCE_DIR/screen.png"
[[ -s "$EVIDENCE_DIR/screen.png" ]] || { echo "FRIDAY_SCREEN_CAPTURE_FAILED" >&2; exit 28; }
