#!/usr/bin/env python3
"""Install an APK only after Android's package and storage services are usable.

Android can report boot completion and answer SettingsProvider while its
emulated-storage FUSE session is still starting. PackageInstaller may then
hang or throw from StorageManagerService. The readiness gate checks mounted
private/emulated volumes; this helper retries only two exact early-boot
signatures and fails closed for unrelated installation errors.
"""
from __future__ import annotations

import argparse
import subprocess
import sys
import time
from typing import Callable


def _text(value: object) -> str:
    if value is None:
        return ""
    if isinstance(value, bytes):
        return value.decode("utf-8", errors="replace")
    return str(value)


def is_transient_storage_race(output: str) -> bool:
    """The known PackageManagerInternal null race during PackageInstaller setup."""
    return (
        "NullPointerException" in output
        and "PackageManagerInternal.freeStorage" in output
        and "StorageManagerService.allocateBytes" in output
    )


def is_transient_storage_session_failure(output: str) -> bool:
    """The emulator's early-boot FUSE/ExternalStorageService session timeout."""
    return (
        "ExternalStorageServiceException" in output
        and "Failed to start session" in output
        and "TimeoutException" in output
    )


def read_storage_diagnostics(adb: str) -> str:
    """Collect only the storage signals needed to classify an install timeout."""
    chunks: list[str] = []
    commands = (
        (adb, "shell", "sm", "list-volumes", "all"),
        (adb, "logcat", "-b", "all", "-d", "-t", "4000"),
    )
    for command in commands:
        try:
            result = subprocess.run(
                list(command),
                capture_output=True,
                text=True,
                timeout=15,
                check=False,
            )
            chunks.append(_text(result.stdout))
            chunks.append(_text(result.stderr))
        except Exception as failure:
            chunks.append(f"{type(failure).__name__}: {failure}")
    return "\n".join(chunks)


def install_with_retry(
    apk: str,
    *,
    max_attempts: int = 24,
    retry_interval_seconds: float = 5.0,
    max_storage_timeouts: int = 2,
    runner: Callable[..., subprocess.CompletedProcess] = subprocess.run,
    sleep: Callable[[float], None] = time.sleep,
    diagnostic_reader: Callable[[str], str] = read_storage_diagnostics,
    adb: str = "adb",
) -> bool:
    if max_attempts < 1 or retry_interval_seconds < 0 or max_storage_timeouts < 0:
        raise ValueError("attempts must be positive and retry budgets non-negative")

    storage_timeouts = 0
    for attempt in range(1, max_attempts + 1):
        try:
            result = runner(
                [adb, "install", "-r", apk],
                capture_output=True,
                text=True,
                timeout=90,
                check=False,
            )
            output = "\n".join(
                part for part in (_text(getattr(result, "stdout", "")), _text(getattr(result, "stderr", ""))) if part
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

            reason = "PackageInstaller reached StorageManager before PackageManagerInternal was ready"
        except subprocess.TimeoutExpired as timeout:
            output = "\n".join((_text(timeout.stdout), _text(timeout.stderr)))
            try:
                diagnostics = diagnostic_reader(adb)
            except Exception as failure:
                diagnostics = f"storage diagnostic failed: {type(failure).__name__}: {failure}"
            combined = output + "\n" + diagnostics
            if is_transient_storage_race(combined):
                reason = "PackageInstaller reached StorageManager before PackageManagerInternal was ready"
            elif is_transient_storage_session_failure(combined):
                storage_timeouts += 1
                if storage_timeouts > max_storage_timeouts:
                    print(
                        "ANDROID_INSTALL_FAILED: Android emulated-storage session did not recover "
                        f"after {max_storage_timeouts} transient timeouts\\n{combined[-4000:]}",
                        file=sys.stderr,
                    )
                    return False
                reason = "Android StorageManager could not start its emulated-storage FUSE session"
            else:
                print(
                    "ANDROID_INSTALL_FAILED: adb install timed out without the known "
                    f"transient storage-session signature on attempt={attempt}\\n{combined[-4000:]}",
                    file=sys.stderr,
                )
                return False

        if attempt == max_attempts:
            print(
                f"ANDROID_INSTALL_FAILED: transient storage race persisted for {max_attempts} attempts",
                file=sys.stderr,
            )
            return False

        print(
            f"ANDROID_INSTALL_RETRY: {reason} "
            f"(attempt {attempt}/{max_attempts}); retrying in {retry_interval_seconds:g}s"
        )
        sleep(retry_interval_seconds)

    return False


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("apk")
    parser.add_argument("--max-attempts", type=int, default=24)
    parser.add_argument("--retry-interval-seconds", type=float, default=5.0)
    parser.add_argument("--max-storage-timeouts", type=int, default=2)
    args = parser.parse_args()
    return 0 if install_with_retry(
        args.apk,
        max_attempts=args.max_attempts,
        retry_interval_seconds=args.retry_interval_seconds,
        max_storage_timeouts=args.max_storage_timeouts,
    ) else 21


if __name__ == "__main__":
    raise SystemExit(main())
