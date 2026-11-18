import importlib.util
import unittest
from pathlib import Path

SCRIPT = Path(__file__).with_name("auto_merge_policy.py")
SPEC = importlib.util.spec_from_file_location("auto_merge_policy", SCRIPT)
MODULE = importlib.util.module_from_spec(SPEC)
assert SPEC and SPEC.loader
SPEC.loader.exec_module(MODULE)


class AutoMergePolicyTests(unittest.TestCase):
    def test_allows_low_risk_application_logic_and_tests(self):
        ok, _ = MODULE.evaluate([
            "mobile/app/src/main/java/com/jarvis/app/formatting/TextFormatter.kt",
            "mobile/app/src/test/java/com/jarvis/app/formatting/TextFormatterTest.kt",
        ])
        self.assertTrue(ok)

    def test_blocks_builder_and_self_modification_control_planes(self):
        for path in (
            "mobile/app/src/main/java/com/jarvis/app/builder/ToolBuilder.kt",
            "mobile/app/src/main/java/com/jarvis/app/evolution/EvolutionEngine.kt",
            "mobile/app/src/main/java/com/jarvis/app/genome/GenomeRegistry.kt",
            "mobile/app/src/main/java/com/jarvis/app/mutant/ArtifactRehydrator.kt",
            "mobile/app/src/main/java/com/jarvis/app/validation/PromotionGate.kt",
            "mobile/app/src/main/java/com/jarvis/app/resource/ResourceGovernor.kt",
            "mobile/app/src/main/java/com/jarvis/app/selfrepair/SelfRepairLoop.kt",
            "mobile/app/src/main/java/com/jarvis/app/cognitive/CognitiveEngine.kt",
        ):
            with self.subTest(path=path):
                self.assertFalse(MODULE.evaluate([path])[0])

    def test_blocks_android_device_and_model_authority_paths(self):
        for path in (
            "mobile/app/src/main/java/com/jarvis/app/android/AndroidMediaControlBackend.kt",
            "mobile/app/src/main/java/com/jarvis/app/model/ModelManager.kt",
            "mobile/app/src/main/java/com/jarvis/app/network/NetworkClient.kt",
        ):
            with self.subTest(path=path):
                self.assertFalse(MODULE.evaluate([path])[0])

    def test_blocks_phone_skill_registry_changes(self):
        ok, reason = MODULE.evaluate([
            "mobile/app/src/main/java/com/jarvis/app/voice/LocalPhoneActionSkillRegistry.kt",
            "mobile/app/src/test/java/com/jarvis/app/voice/PhoneActionPlannerTest.kt",
        ])
        self.assertFalse(ok)
        self.assertIn("protected subsystem", reason)

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

    def test_rejects_production_source_without_package_local_regression_test(self):
        ok, reason = MODULE.evaluate([
            "mobile/app/src/main/java/com/jarvis/app/cognitive/CognitiveEngine.kt",
        ])
        self.assertFalse(ok)
        self.assertIn("lacks a changed regression test", reason)

    def test_rejects_regression_test_from_an_unrelated_package(self):
        ok, _ = MODULE.evaluate([
            "mobile/app/src/main/java/com/jarvis/app/cognitive/CognitiveEngine.kt",
            "mobile/app/src/test/java/com/jarvis/app/android/CognitiveEngineTest.kt",
        ])
        self.assertFalse(ok)

    def test_rejects_test_only_changes_that_could_weaken_the_gate(self):
        ok, reason = MODULE.evaluate([
            "mobile/app/src/test/java/com/jarvis/app/cognitive/CognitiveEngineTest.kt",
        ])
        self.assertFalse(ok)
        self.assertIn("test-only changes require review", reason)

    def test_rejects_resource_only_changes_until_they_have_dedicated_validation(self):
        self.assertFalse(MODULE.evaluate([
            "mobile/app/src/main/res/values/strings.xml",
        ])[0])

    def test_rejects_empty_or_malformed_lists(self):
        self.assertFalse(MODULE.evaluate([])[0])
        self.assertFalse(MODULE.evaluate("not a list")[0])
        self.assertFalse(MODULE.evaluate(["../secret.txt"])[0])
        self.assertFalse(MODULE.evaluate(["./mobile/app/src/main/java/com/jarvis/app/cognitive/Engine.kt"])[0])
        self.assertFalse(MODULE.evaluate(["/mobile/app/src/main/java/com/jarvis/app/cognitive/Engine.kt"])[0])
        self.assertFalse(MODULE.evaluate(["mobile/app/src/main/AndroidManifest.XML"])[0])


if __name__ == "__main__":
    unittest.main()
