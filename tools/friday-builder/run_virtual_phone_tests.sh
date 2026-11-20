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

# Match the owner's actual Realme 9 Pro 5G display geometry from the
# 2026-11-20 device log: 1080x2412 at 480 dpi (360 dp logical width).
# This makes the one-shot UI screenshots representative without live screen
# sharing or requiring the owner to run a test on the handset.
adb shell wm size 1080x2412
adb shell wm density 480
adb shell settings put system font_scale 1.0
{
  echo "Target display: 1080x2412 @ 480 dpi (360 dp logical width)"
  echo "=== wm size ==="
  adb shell wm size 2>&1 || true
  echo "=== wm density ==="
  adb shell wm density 2>&1 || true
  echo "=== app/system locale ==="
  adb shell getprop persist.sys.locale 2>&1 || true
  adb shell getprop ro.build.version.release 2>&1 || true
  echo "=== display cutout/refresh information ==="
  adb shell dumpsys display 2>&1 | grep -m 3 -E 'DisplayDeviceInfo|density|refreshRate' || true
} > "$EVIDENCE_DIR/realme-layout-profile.txt"

./gradlew :mobile:app:connectedDebugAndroidTest

# The instrumentation screenshot sweep captures each user-facing destination
# on this exact virtual device. Pull the app-private evidence directory into
# the run artifact; absence is a failed visual-diagnostics test, not a pass.
UI_AUDIT_DEVICE_DIR="/sdcard/Android/data/com.jarvis.app/files/friday-ui-audit"
UI_AUDIT_PULL_DIR="$EVIDENCE_DIR/ui-audit-pull"
mkdir -p "$UI_AUDIT_PULL_DIR"
if ! adb pull "$UI_AUDIT_DEVICE_DIR" "$UI_AUDIT_PULL_DIR"; then
  echo "FRIDAY_UI_AUDIT_ARTIFACT_MISSING: instrumentation did not produce app screenshots." >&2
  exit 30
fi
UI_AUDIT_MANIFEST="$(find "$UI_AUDIT_PULL_DIR" -type f -name manifest.json -print -quit)"
if [[ -z "$UI_AUDIT_MANIFEST" ]]; then
  echo "FRIDAY_UI_AUDIT_MANIFEST_MISSING: no manifest was pulled from the test device." >&2
  find "$UI_AUDIT_PULL_DIR" -maxdepth 3 -type f -printf '%p\\n' >&2 || true
  exit 31
fi
UI_AUDIT_SOURCE_DIR="$(dirname "$UI_AUDIT_MANIFEST")"

# Validate the visual audit manifest against every captured PNG and produce a
# human-readable index. This is the one-shot UI evidence that can be inspected
# without connecting to or watching the phone live.
python3 - "$UI_AUDIT_SOURCE_DIR" <<'PY'
import hashlib
import html
import json
import pathlib
import sys

root = pathlib.Path(sys.argv[1])
manifest_path = root / "manifest.json"
data = json.loads(manifest_path.read_text(encoding="utf-8"))
screens = data.get("screens", [])
if len(screens) < 18:
    raise SystemExit(f"FRIDAY_UI_AUDIT_INCOMPLETE: expected >=18 screens, got {len(screens)}")
rows = []
for item in screens:
    image = root / item["file"]
    if not image.is_file():
        raise SystemExit(f"FRIDAY_UI_AUDIT_SCREEN_MISSING: {item['file']}")
    digest = hashlib.sha256(image.read_bytes()).hexdigest()
    if digest != item.get("sha256"):
        raise SystemExit(f"FRIDAY_UI_AUDIT_HASH_MISMATCH: {item['file']}")
    rows.append(
        f'<section><h2>{html.escape(item["title"])}</h2>'
        f'<p>{item["width_px"]} × {item["height_px"]} px · SHA-256 verified</p>'
        f'<img loading="lazy" src="{html.escape(item["file"])}"></section>'
    )
(root / "index.html").write_text(
    '<!doctype html><meta charset="utf-8"><title>Friday visual diagnostic</title>'
    '<style>body{font:16px system-ui;max-width:1100px;margin:2rem auto;padding:0 1rem}'
    'img{max-width:100%;height:auto;border:1px solid #888;border-radius:12px}'
    'section{margin:2rem 0}h2{font-size:1.1rem}</style>'
    f'<h1>Friday one-shot visual diagnostic</h1>'
    f'<p>{len(rows)} screens · Android {html.escape(str(data.get("android_release")))} '
    f'API {html.escape(str(data.get("android_sdk")))} · '
    f'{html.escape(str(data.get("device_manufacturer")))} {html.escape(str(data.get("device_model")))}</p>'
    + ''.join(rows),
    encoding="utf-8",
)
(root / "visual-audit-report.md").write_text(
    "# Friday one-shot visual diagnostic\\n\\n"
    f"- Screens captured: {len(rows)}\\n"
    f"- Device profile: {data.get('device_manufacturer')} {data.get('device_model')}\\n"
    f"- Android: {data.get('android_release')} (API {data.get('android_sdk')})\\n"
    "- Every screenshot SHA-256 was checked against the manifest.\\n"
    "- Scope: app UI only; no audio, camera, notifications or off-app recording.\\n"
    "- Open index.html for the complete visual report.\\n",
    encoding="utf-8",
)
print(f"FRIDAY_UI_AUDIT_VERIFIED: {len(rows)} full-screen captures")
PY

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
