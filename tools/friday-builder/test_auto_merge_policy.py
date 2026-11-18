import importlib.util
import unittest
from pathlib import Path

SCRIPT = Path(__file__).with_name("auto_merge_policy.py")
SPEC = importlib.util.spec_from_file_location("auto_merge_policy", SCRIPT)
MODULE = importlib.util.module_from_spec(SPEC)
assert SPEC and SPEC.loader
SPEC.loader.exec_module(MODULE)


class AutoMergePolicyTests(unittest.TestCase):
    def test_allows_low_risk_cognitive_runtime_and_tests(self):
        ok, _ = MODULE.evaluate([
            "mobile/app/src/main/java/com/jarvis/app/cognitive/CognitiveEngine.kt",
            "mobile/app/src/test/java/com/jarvis/app/cognitive/CognitiveEngineTest.kt",
        ])
        self.assertTrue(ok)

    def test_allows_new_host_capability_with_tests(self):
        ok, _ = MODULE.evaluate([
            "mobile/app/src/main/java/com/jarvis/app/android/AndroidMediaControlCapability.kt",
            "mobile/app/src/test/java/com/jarvis/app/android/AndroidMediaControlCapabilityTest.kt",
        ])
        self.assertTrue(ok)

    def test_blocks_manifest_and_permission_surface(self):
        ok, reason = MODULE.evaluate(["mobile/app/src/main/AndroidManifest.xml"])
        self.assertFalse(ok)
        self.assertIn("protected file", reason)

    def test_blocks_identity_and_memory_subsystems(self):
        for path in (
            "mobile/app/src/main/java/com/jarvis/app/identity/SelfModel.kt",
            "mobile/app/src/main/java/com/jarvis/app/memory/ExperienceStore.kt",
            "mobile/app/src/main/java/com/jarvis/app/cognitive/LongTermMemory.kt",
            "mobile/app/src/main/java/com/jarvis/app/cognitive/IdentityState.kt",
        ):
            with self.subTest(path=path):
                self.assertFalse(MODULE.evaluate([path])[0])

    def test_blocks_github_diagnostics_and_builder_control_plane(self):
        for path in (
            "mobile/app/src/main/java/com/jarvis/app/github/GitHubDevelopmentClient.kt",
            "mobile/app/src/main/java/com/jarvis/app/diagnostics/CrashRepairBridge.kt",
            "tools/friday-builder/run_proposal.sh",
            ".github/workflows/build.yml",
        ):
            with self.subTest(path=path):
                self.assertFalse(MODULE.evaluate([path])[0])

    def test_blocks_settings_and_privileged_phone_integrations(self):
        for path in (
            "mobile/app/src/main/java/com/jarvis/app/ui/screens/SettingsScreen.kt",
            "mobile/app/src/main/java/com/jarvis/app/android/ShizukuControlBackend.kt",
            "mobile/app/src/main/java/com/jarvis/app/android/RiskGate.kt",
        ):
            with self.subTest(path=path):
                self.assertFalse(MODULE.evaluate([path])[0])

    def test_blocks_paths_outside_allowlist(self):
        self.assertFalse(MODULE.evaluate(["mobile/app/build.gradle.kts"])[0])
        self.assertFalse(MODULE.evaluate([".gitignore"])[0])

    def test_rejects_empty_or_malformed_lists(self):
        self.assertFalse(MODULE.evaluate([])[0])
        self.assertFalse(MODULE.evaluate("not a list")[0])
        self.assertFalse(MODULE.evaluate(["../secret.txt"])[0])
        self.assertFalse(MODULE.evaluate(["./mobile/app/src/main/java/com/jarvis/app/cognitive/Engine.kt"])[0])
        self.assertFalse(MODULE.evaluate(["/mobile/app/src/main/java/com/jarvis/app/cognitive/Engine.kt"])[0])
        self.assertFalse(MODULE.evaluate(["mobile/app/src/main/AndroidManifest.XML"])[0])


if __name__ == "__main__":
    unittest.main()
