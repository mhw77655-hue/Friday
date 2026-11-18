#!/usr/bin/env python3
"""Wait for Android system services and storage mounts used by PackageInstaller.

Boot-completed, package enumeration and SettingsProvider do not prove that
Android's emulated-storage FUSE session is mounted. PackageInstaller can hang
or fail while StorageManager is still bringing that session online, so this
probe requires both private and emulated volumes to report mounted.
"""
from __future__ import annotations

import argparse
import pathlib
import re
import subprocess
import sys
import time
from typing import Callable


def run_adb(*args: str) -> str:
    result = subprocess.run(
        ["adb", *args],
        capture_output=True,
        text=True,
        timeout=15,
        check=False,
    )
    if result.returncode != 0:
        return (result.stdout + "\n" + result.stderr).strip()
    return result.stdout.strip()


def probe_state() -> dict[str, str]:
    return {
        "boot_completed": run_adb("shell", "getprop", "sys.boot_completed"),
        "packages": run_adb("shell", "pm", "list", "packages"),
        "settings_device_provisioned": run_adb(
            "shell", "settings", "get", "global", "device_provisioned"
        ),
        # PackageInstaller also needs the emulated-storage FUSE session. Boot
        # completion and SettingsProvider can be ready while this mount is not.
        "storage_volumes": run_adb("shell", "sm", "list-volumes", "all"),
    }


def is_system_ready(state: dict[str, str]) -> bool:
    """Require boot, PackageManager, SettingsProvider, and mounted private/emulated storage."""
    packages = set(state.get("packages", "").splitlines())
    settings_value = state.get("settings_device_provisioned", "").strip()
    volume_lines = [line.split() for line in state.get("storage_volumes", "").splitlines()]
    private_mounted = any(
        len(parts) >= 2 and parts[0].startswith("private") and parts[1] == "mounted"
        for parts in volume_lines
    )
    emulated_mounted = any(
        len(parts) >= 2 and parts[0] == "emulated" and parts[1] == "mounted"
        for parts in volume_lines
    )
    return (
        state.get("boot_completed", "").strip() == "1"
        and "package:android" in packages
        and "package:com.android.settings" in packages
        and re.fullmatch(r"[01]", settings_value) is not None
        and private_mounted
        and emulated_mounted
    )


def collect_diagnostics() -> str:
    commands = (
        ("boot properties", ("shell", "getprop")),
        ("package service", ("shell", "service", "check", "package")),
        ("package enumeration", ("shell", "pm", "list", "packages")),
        ("Settings provider probe", ("shell", "settings", "get", "global", "device_provisioned")),
        ("storage volume state", ("shell", "sm", "list-volumes", "all")),
        ("mount service diagnostics", ("shell", "dumpsys", "mount")),
        ("registered content providers", ("shell", "dumpsys", "activity", "providers")),
        ("PackageInstaller diagnostics", ("shell", "dumpsys", "package")),
        ("system log", ("logcat", "-b", "all", "-d", "-t", "16000")),
    )
    output: list[str] = []
    for label, args in commands:
        output.append(f"=== {label} ===")
        try:
            output.append(run_adb(*args))
        except Exception as exc:  # diagnostics must not hide the original readiness failure
            output.append(f"diagnostic command failed: {type(exc).__name__}: {exc}")
    return "\n".join(output) + "\n"


def wait_for_system(
    timeout_seconds: int,
    interval_seconds: int,
    evidence_file: pathlib.Path,
    probe: Callable[[], dict[str, str]] = probe_state,
    sleep: Callable[[float], None] = time.sleep,
) -> bool:
    deadline = time.monotonic() + timeout_seconds
    last_state: dict[str, str] = {}
    while True:
        try:
            last_state = probe()
        except Exception as exc:
            last_state = {"probe_error": f"{type(exc).__name__}: {exc}"}
        if is_system_ready(last_state):
            print(
                "ANDROID_SYSTEM_READY: boot_completed=1 package_manager=responsive "
                "settings_provider=ready private_storage=mounted emulated_storage=mounted "
                f"device_provisioned={last_state['settings_device_provisioned'].strip()}"
            )
            return True
        remaining = deadline - time.monotonic()
        if remaining <= 0:
            evidence_file.parent.mkdir(parents=True, exist_ok=True)
            evidence_file.write_text(
                "Last readiness probe:\n"
                + repr(last_state)
                + "\n\n"
                + collect_diagnostics(),
                encoding="utf-8",
            )
            print(
                "ANDROID_SYSTEM_NOT_READY: PackageManager/SettingsProvider "
                f"readiness timed out; evidence={evidence_file}",
                file=sys.stderr,
            )
            return False
        print(
            "Waiting for Android system providers: "
            f"boot={last_state.get('boot_completed', '<unknown>')} "
            f"settings={last_state.get('settings_device_provisioned', '<unavailable>')} "
            f"storage={last_state.get('storage_volumes', '<unavailable>')}"
        )
        sleep(min(interval_seconds, remaining))


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--timeout-seconds", type=int, default=300)
    parser.add_argument("--interval-seconds", type=int, default=5)
    parser.add_argument("--evidence-file", type=pathlib.Path, required=True)
    args = parser.parse_args()
    if args.timeout_seconds <= 0 or args.interval_seconds <= 0:
        parser.error("timeout and interval must be positive")
    return 0 if wait_for_system(
        args.timeout_seconds, args.interval_seconds, args.evidence_file
    ) else 20


if __name__ == "__main__":
    raise SystemExit(main())
