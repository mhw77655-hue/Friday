#!/usr/bin/env python3
"""Retry APK installation only for Android's early-boot PackageInstaller race.

Some emulator images report boot completion and expose SettingsProvider before
StorageManagerService has a live PackageManagerInternal. PackageInstaller then
throws a NullPointerException from freeStorage while streaming the APK. This is
transient boot ordering, not an APK failure; retry that exact signature only.
"""
from __future__ import annotations

import argparse
import subprocess
import sys
import time
from typing import Callable, Sequence


TRANSIENT_STORAGE_RACE = (
    "NullPointerException" in " ".join(("",))  # replaced by signature predicate below
)


def is_transient_storage_race(output: str) -> bool:
    return (
        "NullPointerException" in output
        and "PackageManagerInternal.freeStorage" in output
        and "StorageManagerService.allocateBytes" in output
    )


def install_with_retry(
    apk: str,
    *,
    max_attempts: int = 24,
    retry_interval_seconds: float = 5.0,
    runner: Callable[..., subprocess.CompletedProcess] = subprocess.run,
    sleep: Callable[[float], None] = time.sleep,
    adb: str = "adb",
) -> bool:
    if max_attempts < 1 or retry_interval_seconds < 0:
        raise ValueError("max_attempts must be positive and retry interval non-negative")

    for attempt in range(1, max_attempts + 1):
        result = runner(
            [adb, "install", "-r", apk],
            capture_output=True,
            text=True,
            timeout=180,
            check=False,
        )
        output = "\n".join(
            part for part in (getattr(result, "stdout", ""), getattr(result, "stderr", "")) if part
        )
        if result.returncode == 0:
            if output:
                print(output)
            print(f"ANDROID_INSTALL_SUCCEEDED: attempt={attempt}")
            return True

        if not is_transient_storage_race(output):
            print(
                f"ANDROID_INSTALL_FAILED: non-retryable failure on attempt={attempt}\n{output}",
                file=sys.stderr,
            )
            return False

        if attempt == max_attempts:
            print(
                f"ANDROID_INSTALL_FAILED: PackageInstaller storage race persisted for "
                f"{max_attempts} attempts\n{output}",
                file=sys.stderr,
            )
            return False

        print(
            "ANDROID_INSTALL_RETRY: PackageInstaller reached StorageManager before "
            f"PackageManagerInternal was ready (attempt {attempt}/{max_attempts}); "
            f"retrying in {retry_interval_seconds:g}s"
        )
        sleep(retry_interval_seconds)

    return False


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("apk")
    parser.add_argument("--max-attempts", type=int, default=24)
    parser.add_argument("--retry-interval-seconds", type=float, default=5.0)
    args = parser.parse_args()
    return 0 if install_with_retry(
        args.apk,
        max_attempts=args.max_attempts,
        retry_interval_seconds=args.retry_interval_seconds,
    ) else 21


if __name__ == "__main__":
    raise SystemExit(main())
