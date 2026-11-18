#!/usr/bin/env bash
set -euo pipefail

EVIDENCE_DIR="${RUNNER_TEMP}/friday-emulator"
mkdir -p "$EVIDENCE_DIR"
adb logcat -c

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
    echo "=== Settings provider registration ==="
    adb shell dumpsys activity providers 2>&1 || true
    echo "=== PackageInstaller service ==="
    adb shell dumpsys package 2>&1 | grep -i -C 3 'PackageInstaller' || true
    echo "=== system_server and provider boot log ==="
    adb logcat -b all -d -t 16000 2>&1 || true
  } > "$EVIDENCE_DIR/boot-readiness-diagnostics.txt"
}

trap 'adb logcat -d -t 16000 > "$EVIDENCE_DIR/logcat.txt" 2>/dev/null || true; adb shell dumpsys activity services > "$EVIDENCE_DIR/services.txt" 2>/dev/null || true; adb shell dumpsys package com.jarvis.app > "$EVIDENCE_DIR/package.txt" 2>/dev/null || true' EXIT

# sys.boot_completed and package enumeration alone are insufficient: PackageManager
# can list packages before the Settings ContentProvider has been registered.
# PackageInstaller calls Settings during session creation, so probe the actual
# provider before attempting adb install. Every condition is observed, not slept
# for a guessed fixed startup duration.
system_ready=0
for attempt in $(seq 1 60); do
  boot_completed="$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' || true)"
  package_list="$(adb shell pm list packages 2>/dev/null || true)"
  settings_probe="$(adb shell settings get global device_provisioned 2>&1 | tr -d '\r' || true)"

  if [[ "$boot_completed" == "1" ]] &&
     grep -q '^package:android$' <<< "$package_list" &&
     grep -q '^package:com.android.settings$' <<< "$package_list" &&
     [[ "$settings_probe" =~ ^[0-9]+$ ]]; then
    system_ready=1
    echo "ANDROID_SYSTEM_READY: boot_completed=1 package_manager=responsive settings_provider=ready device_provisioned=$settings_probe"
    break
  fi

  echo "Waiting for Android system providers ($attempt/60): boot=$boot_completed settings_probe=${settings_probe:-<unavailable>}"
  sleep 5
done

if [[ "$system_ready" != "1" ]]; then
  echo "Android system did not reach PackageManager + Settings-provider readiness." >&2
  capture_boot_diagnostics
  cat "$EVIDENCE_DIR/boot-readiness-diagnostics.txt" >&2
  exit 20
fi

# Keep install failure distinct from boot-readiness failure and retain the exact
# provider/system evidence needed to diagnose a PackageInstaller regression.
if ! adb install -r mobile/app/build/outputs/apk/debug/app-debug.apk; then
  echo "ANDROID_INSTALL_FAILED: PackageInstaller rejected the APK after readiness probes." >&2
  capture_boot_diagnostics
  cat "$EVIDENCE_DIR/boot-readiness-diagnostics.txt" >&2
  exit 21
fi

python3 tools/friday-builder/apply_device_state_capsule.py .github/android-emulator/default-state-capsule.json
./gradlew :mobile:app:connectedDebugAndroidTest
adb shell am start -n com.jarvis.app/.MainActivity || true
sleep 8
adb shell dumpsys activity top > "$EVIDENCE_DIR/activity-top.txt" 2>/dev/null || true
adb exec-out screencap -p > "$EVIDENCE_DIR/screen.png" || true
