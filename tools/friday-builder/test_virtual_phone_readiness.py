import unittest

from wait_for_android_system import is_system_ready


class AndroidSystemReadinessTests(unittest.TestCase):
    def state(self, **overrides):
        value = {
            "boot_completed": "1",
            "packages": "package:android\npackage:com.android.settings\n",
            "settings_device_provisioned": "0",
            "storage_volumes": "private mounted null\nemulated mounted null",
        }
        value.update(overrides)
        return value

    def test_ready_only_when_settings_provider_answers(self):
        self.assertTrue(is_system_ready(self.state()))

    def test_package_enumeration_alone_does_not_prove_settings_provider_ready(self):
        self.assertFalse(
            is_system_ready(self.state(settings_device_provisioned="Cannot access system provider"))
        )

    def test_boot_completed_is_required(self):
        self.assertFalse(is_system_ready(self.state(boot_completed="0")))

    def test_required_system_packages_are_required(self):
        self.assertFalse(
            is_system_ready(self.state(packages="package:android\n"))
        )

    def test_settings_and_package_manager_can_be_ready_before_storage_mounts(self):
        self.assertFalse(
            is_system_ready(self.state(storage_volumes="private unmounted null\nemulated unmounted null"))
        )

    def test_accepts_user_scoped_volume_identifiers(self):
        self.assertTrue(
            is_system_ready(self.state(
                storage_volumes="private:179,64 mounted null\nemulated;0 mounted null"
            ))
        )

    def test_private_storage_must_be_mounted(self):
        self.assertFalse(
            is_system_ready(self.state(storage_volumes="private unmounted null\nemulated mounted null"))
        )

    def test_emulated_storage_must_be_mounted_for_package_installer(self):
        self.assertFalse(
            is_system_ready(self.state(storage_volumes="private mounted null\nemulated unmounted null"))
        )

    def test_null_or_unknown_settings_value_is_not_ready(self):
        for value in ("", "null", "unknown", "Exception occurred"):
            with self.subTest(value=value):
                self.assertFalse(
                    is_system_ready(self.state(settings_device_provisioned=value))
                )



    def test_wait_for_system_returns_immediately_when_probe_is_ready(self):
        from wait_for_android_system import wait_for_system
        import tempfile
        from pathlib import Path

        with tempfile.TemporaryDirectory() as directory:
            evidence = Path(directory) / "evidence.txt"
            calls = []
            state = self.state()
            self.assertTrue(wait_for_system(10, 1, evidence, probe=lambda: calls.append(1) or state, sleep=lambda _: None))
            self.assertEqual([1], calls)
            self.assertFalse(evidence.exists())

    def test_wait_for_system_persists_last_state_and_diagnostics_on_timeout(self):
        from wait_for_android_system import wait_for_system
        import tempfile
        from pathlib import Path
        from unittest.mock import patch

        with tempfile.TemporaryDirectory() as directory:
            evidence = Path(directory) / "evidence.txt"
            clock = iter([0.0, 2.0])
            state = self.state(boot_completed="0")
            with patch("wait_for_android_system.time.monotonic", side_effect=lambda: next(clock)), \\
                 patch("wait_for_android_system.collect_diagnostics", return_value="diagnostic-tail"):
                self.assertFalse(wait_for_system(1, 1, evidence, probe=lambda: state, sleep=lambda _: None))
            content = evidence.read_text(encoding="utf-8")
            self.assertIn("boot_completed", content)
            self.assertIn("diagnostic-tail", content)

    def test_run_adb_returns_output_and_combines_failure_diagnostics(self):
        from wait_for_android_system import run_adb
        from types import SimpleNamespace
        from unittest.mock import patch

        with patch("wait_for_android_system.subprocess.run", return_value=SimpleNamespace(returncode=0, stdout="ready\n", stderr="")):
            self.assertEqual("ready", run_adb("shell", "getprop"))
        with patch("wait_for_android_system.subprocess.run", return_value=SimpleNamespace(returncode=1, stdout="out", stderr="err")):
            self.assertEqual("out\nerr", run_adb("shell", "broken"))

    def test_probe_state_queries_each_readiness_dependency(self):
        from wait_for_android_system import probe_state
        from unittest.mock import patch

        with patch("wait_for_android_system.run_adb", side_effect=["1", "package:android", "0", "private mounted\nemulated mounted"]) as run:
            state = probe_state()
        self.assertEqual("1", state["boot_completed"])
        self.assertEqual("private mounted\nemulated mounted", state["storage_volumes"])
        self.assertEqual(4, run.call_count)


if __name__ == "__main__":
    unittest.main()
