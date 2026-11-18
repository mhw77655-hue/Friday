#!/usr/bin/env python3
"""Validate and apply a privacy-safe Android virtual-device state capsule.

The capsule describes device conditions only. It is never used to copy app
accounts, credentials, messages, photos, contacts, notifications or private
application data into the emulator.
"""
from __future__ import annotations

import json
import re
import subprocess
import sys
from pathlib import Path
from typing import Any

PERMISSION_STATES = {"granted", "denied", "not_applicable", "unavailable", "disabled"}
ALLOWED_PERMISSION_KEYS = {
    "camera", "microphone", "notifications", "all_files_access",
    "accessibility", "notification_listener", "shizuku",
}
RUNTIME_PERMISSIONS = {
    "camera": "android.permission.CAMERA",
    "microphone": "android.permission.RECORD_AUDIO",
    "notifications": "android.permission.POST_NOTIFICATIONS",
}


def validate_capsule(data: Any) -> dict[str, Any]:
    if not isinstance(data, dict):
        raise ValueError("capsule must be a JSON object")
    required = {
        "schema_version", "profile", "api_level", "locale", "time_zone",
        "display", "permissions", "battery", "network", "privacy_scope",
        "limitations",
    }
    if set(data) != required:
        raise ValueError("capsule keys do not match the version-1 allowlist")
    if data["schema_version"] != 1:
        raise ValueError("unsupported capsule schema_version")
    if not isinstance(data["profile"], str) or not re.fullmatch(r"[a-z0-9_]{1,48}", data["profile"]):
        raise ValueError("invalid emulator profile")
    if type(data["api_level"]) is not int or not 26 <= data["api_level"] <= 36:
        raise ValueError("api_level must be an integer from 26 to 36")
    if not isinstance(data["locale"], str) or not re.fullmatch(r"[A-Za-z]{2,3}(?:-[A-Za-z0-9]{2,8}){0,3}", data["locale"]):
        raise ValueError("invalid locale")
    if not isinstance(data["time_zone"], str) or not re.fullmatch(r"[A-Za-z0-9_+./-]{1,64}", data["time_zone"]):
        raise ValueError("invalid time_zone")

    display = data["display"]
    if not isinstance(display, dict) or set(display) != {"width_px", "height_px", "density_dpi"}:
        raise ValueError("display must contain width_px, height_px and density_dpi only")
    for key, low, high in (("width_px", 320, 4096), ("height_px", 480, 8192), ("density_dpi", 80, 1000)):
        if type(display[key]) is not int or not low <= display[key] <= high:
            raise ValueError(f"display.{key} is outside its allowed range")

    permissions = data["permissions"]
    if not isinstance(permissions, dict) or set(permissions) != ALLOWED_PERMISSION_KEYS:
        raise ValueError("permissions must contain the complete allowlisted key set")
    if any(value not in PERMISSION_STATES for value in permissions.values()):
        raise ValueError("permission states must use the documented allowlist")

    battery = data["battery"]
    if not isinstance(battery, dict) or set(battery) != {"level", "charging"}:
        raise ValueError("battery must contain level and charging only")
    if type(battery["level"]) is not int or not 0 <= battery["level"] <= 100:
        raise ValueError("battery.level must be between 0 and 100")
    if type(battery["charging"]) is not bool:
        raise ValueError("battery.charging must be boolean")

    network = data["network"]
    if not isinstance(network, dict) or set(network) != {"transport", "connected"}:
        raise ValueError("network must contain transport and connected only")
    if network["transport"] not in {"wifi", "cellular", "ethernet", "offline"}:
        raise ValueError("unsupported network transport")
    if type(network["connected"]) is not bool:
        raise ValueError("network.connected must be boolean")

    if data["privacy_scope"] != "synthetic_test_baseline_no_personal_data":
        raise ValueError("capsule privacy_scope is not approved")
    if not isinstance(data["limitations"], list) or len(data["limitations"]) > 20:
        raise ValueError("limitations must be a list of at most 20 items")
    if any(not isinstance(item, str) or len(item) > 240 for item in data["limitations"]):
        raise ValueError("invalid limitation entry")
    return data


def adb(*args: str) -> None:
    subprocess.run(["adb", "shell", *args], check=True, timeout=20)


def verify_runtime_defaults(
    data: dict[str, Any],
    property_reader: Any,
) -> None:
    # Do not write similarly named Settings keys and pretend they changed the
    # device configuration. The emulator is launched with an explicit timezone;
    # locale is checked against the effective boot properties.
    locale_value = property_reader("persist.sys.locale") or property_reader("ro.product.locale")
    actual_locale = locale_value.split(",")[0].strip()
    if actual_locale != data["locale"]:
        raise ValueError(
            f"capsule locale {data['locale']} does not match emulator locale {actual_locale or '<unknown>'}"
        )

    actual_timezone = property_reader("persist.sys.timezone").strip()
    accepted_timezones = {data["time_zone"]}
    if data["time_zone"] == "UTC":
        accepted_timezones.add("Etc/UTC")
    if actual_timezone not in accepted_timezones:
        raise ValueError(
            f"capsule time_zone {data['time_zone']} does not match emulator timezone {actual_timezone or '<unknown>'}"
        )


def apply_capsule(data: dict[str, Any]) -> None:
    def read_property(name: str) -> str:
        return subprocess.check_output(["adb", "shell", "getprop", name], text=True, timeout=10).strip()

    actual_api = int(read_property("ro.build.version.sdk"))
    if actual_api != data["api_level"]:
        raise ValueError(f"capsule api_level {data['api_level']} does not match emulator API {actual_api}")
    verify_runtime_defaults(data, read_property)
    display = data["display"]
    adb("wm", "size", f"{display['width_px']}x{display['height_px']}")
    adb("wm", "density", str(display["density_dpi"]))

    battery = data["battery"]
    adb("dumpsys", "battery", "set", "level", str(battery["level"]))
    adb("dumpsys", "battery", "set", "status", "2" if battery["charging"] else "3")

    for key, permission in RUNTIME_PERMISSIONS.items():
        state = data["permissions"][key]
        if state == "not_applicable":
            continue
        if state not in {"granted", "denied"}:
            raise ValueError(f"{key} must be granted, denied or not_applicable in an executable capsule")
        adb("pm", "grant" if state == "granted" else "revoke", "com.jarvis.app", permission)

    all_files = data["permissions"]["all_files_access"]
    if all_files in {"granted", "denied"}:
        adb("appops", "set", "com.jarvis.app", "MANAGE_EXTERNAL_STORAGE", "allow" if all_files == "granted" else "deny")

    accessibility = data["permissions"]["accessibility"] == "granted"
    accessibility_component = "com.jarvis.app/com.jarvis.app.voice.FridayAccessibilityService"
    adb("settings", "put", "secure", "enabled_accessibility_services", accessibility_component if accessibility else "")
    adb("settings", "put", "secure", "accessibility_enabled", "1" if accessibility else "0")

    listener = data["permissions"]["notification_listener"] == "granted"
    listener_component = "com.jarvis.app/com.jarvis.app.JarvisNotificationListener"
    adb("settings", "put", "secure", "enabled_notification_listeners", listener_component if listener else "")

    for command in network_commands(data["network"]):
        adb(*command)


def network_commands(network: dict[str, Any]) -> tuple[tuple[str, ...], ...]:
    """Translate the capsule's desired connectivity into explicit ADB actions.

    Disconnected state takes precedence over transport. A capsule may describe
    the last available transport while intentionally requesting offline mode.
    """
    transport = network["transport"]
    if not network["connected"] or transport == "offline":
        return (("svc", "wifi", "disable"), ("svc", "data", "disable"))
    if transport == "wifi":
        return (("svc", "wifi", "enable"), ("svc", "data", "disable"))
    if transport == "cellular":
        return (("svc", "wifi", "disable"), ("svc", "data", "enable"))
    # Emulator Ethernet is provided by the virtual NIC; leave Wi-Fi and mobile
    # data disabled so the AVD uses its existing Ethernet-backed connection.
    return (("svc", "wifi", "disable"), ("svc", "data", "disable"))


def main() -> int:
    if len(sys.argv) != 2:
        print("usage: apply_device_state_capsule.py CAPSULE.json", file=sys.stderr)
        return 2
    path = Path(sys.argv[1])
    try:
        data = validate_capsule(json.loads(path.read_text(encoding="utf-8")))
        apply_capsule(data)
    except Exception as exc:
        print(f"DEVICE_STATE_CAPSULE_FAILED: {exc}", file=sys.stderr)
        return 1
    print(
        "DEVICE_STATE_CAPSULE_APPLIED: "
        f"profile={data['profile']} api={data['api_level']} "
        f"display={data['display']['width_px']}x{data['display']['height_px']} "
        f"permissions=allowlisted battery={data['battery']['level']}%"
    )
    print("DEVICE_STATE_CAPSULE_PRIVACY: synthetic baseline only; no personal app data copied")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
