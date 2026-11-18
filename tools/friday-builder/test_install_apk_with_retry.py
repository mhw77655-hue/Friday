import subprocess
import unittest
from unittest.mock import Mock

from install_apk_with_retry import (
    install_with_retry,
    is_transient_storage_race,
    is_transient_storage_session_failure,
)


class InstallRetryTests(unittest.TestCase):
    TRANSIENT = (
        "Exception occurred while executing 'install': "
        "java.lang.NullPointerException: Attempt to invoke virtual method "
        "'void android.content.pm.PackageManagerInternal.freeStorage(java.lang.String, long, int)' "
        "on a null object reference at "
        "com.android.server.StorageManagerService.allocateBytes(StorageManagerService.java:4205)"
    )

    def result(self, code, stdout="", stderr=""):
        return Mock(returncode=code, stdout=stdout, stderr=stderr)

    def test_recognizes_only_the_specific_early_boot_storage_race(self):
        self.assertTrue(is_transient_storage_race(self.TRANSIENT))
        self.assertFalse(is_transient_storage_race("INSTALL_FAILED_INVALID_APK"))
        self.assertFalse(is_transient_storage_race("NullPointerException in unrelated code"))

    STORAGE_TIMEOUT = (
        "StorageManagerService: ExternalStorageServiceException: Failed to start session "
        "Caused by: java.util.concurrent.TimeoutException"
    )

    def test_recognizes_only_the_specific_emulated_storage_session_timeout(self):
        self.assertTrue(is_transient_storage_session_failure(self.STORAGE_TIMEOUT))
        self.assertFalse(is_transient_storage_session_failure("INSTALL_FAILED_INVALID_APK"))
        self.assertFalse(is_transient_storage_session_failure("StorageManagerService is running"))

    def test_uses_non_streaming_adb_install_to_avoid_early_boot_fuse_sessions(self):
        runner = Mock(return_value=self.result(0, "Success"))
        self.assertTrue(install_with_retry("app.apk", runner=runner))
        command = runner.call_args.args[0]
        self.assertEqual(command, ["adb", "install", "--no-streaming", "-r", "app.apk"])
        self.assertEqual(runner.call_args.kwargs["timeout"], 180)

    def test_first_install_success_does_not_retry(self):
        runner = Mock(return_value=self.result(0, "Success"))
        sleep = Mock()
        self.assertTrue(install_with_retry("app.apk", runner=runner, sleep=sleep))
        runner.assert_called_once()
        sleep.assert_not_called()

    def test_transient_storage_race_retries_until_success(self):
        runner = Mock(side_effect=[
            self.result(1, stderr=self.TRANSIENT),
            self.result(1, stderr=self.TRANSIENT),
            self.result(0, stdout="Success"),
        ])
        sleep = Mock()
        self.assertTrue(install_with_retry(
            "app.apk", max_attempts=3, retry_interval_seconds=2,
            runner=runner, sleep=sleep,
        ))
        self.assertEqual(runner.call_count, 3)
        self.assertEqual([call.args[0] for call in sleep.call_args_list], [2, 2])

    def test_permanent_install_failure_is_not_retried(self):
        runner = Mock(return_value=self.result(1, stderr="INSTALL_FAILED_INVALID_APK"))
        sleep = Mock()
        self.assertFalse(install_with_retry("app.apk", runner=runner, sleep=sleep))
        runner.assert_called_once()
        sleep.assert_not_called()

    def test_exhausted_transient_retries_fail_closed(self):
        runner = Mock(return_value=self.result(1, stderr=self.TRANSIENT))
        sleep = Mock()
        self.assertFalse(install_with_retry(
            "app.apk", max_attempts=3, runner=runner, sleep=sleep,
        ))
        self.assertEqual(runner.call_count, 3)
        self.assertEqual(sleep.call_count, 2)

    def test_install_timeout_retries_only_when_storage_session_failure_is_confirmed(self):
        runner = Mock(side_effect=[
            subprocess.TimeoutExpired(cmd=["adb", "install"], timeout=90),
            self.result(0, stdout="Success"),
        ])
        diagnostics = Mock(return_value=self.STORAGE_TIMEOUT)
        sleep = Mock()
        self.assertTrue(install_with_retry(
            "app.apk", runner=runner, diagnostic_reader=diagnostics, sleep=sleep,
        ))
        self.assertEqual(runner.call_count, 2)
        diagnostics.assert_called_once_with("adb")
        sleep.assert_called_once_with(5.0)

    def test_unexplained_install_timeout_fails_closed(self):
        runner = Mock(side_effect=subprocess.TimeoutExpired(cmd=["adb", "install"], timeout=90))
        diagnostics = Mock(return_value="no storage service failure in logcat")
        sleep = Mock()
        self.assertFalse(install_with_retry(
            "app.apk", runner=runner, diagnostic_reader=diagnostics, sleep=sleep,
        ))
        runner.assert_called_once()
        sleep.assert_not_called()

    def test_emulated_storage_timeout_retry_budget_is_bounded(self):
        runner = Mock(side_effect=[
            subprocess.TimeoutExpired(cmd=["adb", "install"], timeout=90),
            subprocess.TimeoutExpired(cmd=["adb", "install"], timeout=90),
            subprocess.TimeoutExpired(cmd=["adb", "install"], timeout=90),
        ])
        diagnostics = Mock(return_value=self.STORAGE_TIMEOUT)
        sleep = Mock()
        self.assertFalse(install_with_retry(
            "app.apk", max_attempts=5, max_storage_timeouts=2,
            runner=runner, diagnostic_reader=diagnostics, sleep=sleep,
        ))
        self.assertEqual(runner.call_count, 3)
        self.assertEqual(sleep.call_count, 2)

    def test_rejects_invalid_retry_policy(self):
        with self.assertRaises(ValueError):
            install_with_retry("app.apk", max_attempts=0)


if __name__ == "__main__":
    unittest.main()
