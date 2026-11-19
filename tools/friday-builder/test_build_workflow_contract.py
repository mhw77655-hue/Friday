import re
import unittest
from pathlib import Path

ROOT = Path(__file__).parents[2]
WORKFLOW = ROOT / ".github" / "workflows" / "build.yml"


class BuildWorkflowContractTests(unittest.TestCase):
    def setUp(self):
        self.workflow = WORKFLOW.read_text(encoding="utf-8")

    def job_ids(self):
        jobs = self.workflow.split("\njobs:\n", 1)[1]
        return re.findall(r"^  ([a-z][a-z0-9_-]*):\s*$", jobs, flags=re.MULTILINE)

    def test_required_job_ids_are_unique_and_canonical(self):
        ids = self.job_ids()
        self.assertEqual(
            ids,
            ["debug-apk", "android-emulator", "full-source-audit", "report-failure"],
        )
        self.assertEqual(len(ids), len(set(ids)))

    def test_release_signing_smoke_runs_inside_debug_validation(self):
        debug = self.workflow.split("  debug-apk:", 1)[1].split("  android-emulator:", 1)[0]
        self.assertEqual(debug.count("Detect release-signing changes"), 1)
        self.assertEqual(debug.count("Build and verify release variant with a disposable CI key"), 1)
        self.assertIn("steps.release_signing_diff.outputs.run_smoke == 'true'", debug)

    def test_emulator_and_source_audit_are_required_failure_report_dependencies(self):
        reporter = self.workflow.split("  report-failure:", 1)[1]
        for job in ("debug-apk", "android-emulator", "full-source-audit"):
            self.assertIn(f"- {job}", reporter)
            self.assertIn(f"needs.{job}.result != 'success'", reporter)

    def test_emulator_requires_usable_kvm_instead_of_silently_running_tcg(self):
        self.assertIn("Enable KVM acceleration for the disposable phone", self.workflow)
        self.assertIn("sudo chmod 666 /dev/kvm", self.workflow)
        self.assertIn("ANDROID_KVM_READY", self.workflow)
        self.assertIn("x86_64 software emulation is too slow and unreliable", self.workflow)
        self.assertIn("-memory 4096 -cores 4", self.workflow)

    def test_virtual_phone_requires_real_friday_install_and_foreground_launch(self):
        script = (ROOT / "tools" / "friday-builder" / "run_virtual_phone_tests.sh").read_text(encoding="utf-8")
        self.assertIn("install_apk_with_retry.py mobile/app/build/outputs/apk/debug/app-debug.apk", script)
        self.assertIn("adb shell pm grant com.jarvis.app android.permission.RECORD_AUDIO", script)
        self.assertIn("synthetic capsule deliberately begins with microphone access denied", script)
        self.assertIn("adb shell pm path com.jarvis.app", script)
        self.assertIn("adb shell am start -W -n com.jarvis.app/.MainActivity", script)
        self.assertIn("Status: ok", script)
        self.assertIn("ACTIVITY com.jarvis.app/.MainActivity", script)
        self.assertIn("FRIDAY_PROCESS_MISSING", script)
        self.assertNotIn("am start -n com.jarvis.app/.MainActivity || true", script)

    def test_workflow_dispatch_is_available_for_trusted_publisher(self):
        self.assertIn("workflow_dispatch:", self.workflow)

    def test_android_emulator_matches_github_runner_resource_budget(self):
        emulator = self.workflow.split("  android-emulator:", 1)[1].split("  full-source-audit:", 1)[0]
        self.assertIn('"system-images;android-35;google_apis;x86_64"', emulator)
        self.assertIn("target: google_apis", emulator)
        self.assertIn("cores: 4", emulator)
        self.assertIn("ram-size: 4096M", emulator)
        self.assertIn("-memory 4096 -cores 4", emulator)
        self.assertNotIn("-cores 2", emulator)

    def test_actionlint_ignores_only_its_stale_concurrency_queue_schema_error(self):
        self.assertIn("actionlint\" -ignore 'unexpected key \\\"queue\\\" for \\\"concurrency\\\" section' -color", self.workflow)
        self.assertIn("actionlint's pinned schema predates GitHub's documented concurrency.queue key", self.workflow)

    def test_concurrency_preserves_all_pending_validation_runs(self):
        concurrency = self.workflow.split("concurrency:", 1)[1].split("permissions:", 1)[0]
        self.assertIn("queue: max", concurrency)
        self.assertIn("cancel-in-progress: false", concurrency)
        self.assertNotIn("retains the newest pending run", concurrency)

    def test_failure_report_runs_for_failed_skipped_or_cancelled_required_jobs(self):
        reporter = self.workflow.split("  report-failure:", 1)[1]
        self.assertIn("if: ${{ always() &&", reporter)
        for job in ("debug-apk", "android-emulator", "full-source-audit"):
            self.assertIn(f"needs.{job}.result != 'success'", reporter)
        self.assertIn("A skipped or cancelled lane is not a pass", reporter)
        self.assertIn("DEBUG_JOB_RESULT", reporter)
        self.assertIn("EMULATOR_JOB_RESULT", reporter)
        self.assertIn("SOURCE_AUDIT_JOB_RESULT", reporter)

    def test_rea_contract_covers_methods_without_bytecode(self):
        source = (ROOT / "mobile" / "app" / "src" / "main" / "java" / "com" / "jarvis" / "app" / "rea" / "DexStructureInspector.kt").read_text(encoding="utf-8")
        self.assertIn("val body = implementation ?: return null", source)
        self.assertIn("extractMethodEvidence(signature, method.implementation)", source)
        self.assertNotIn("method.implementation.instructions", source)

    def test_candidate_branches_do_not_run_duplicate_push_and_pull_request_builds(self):
        push = self.workflow.split("  push:", 1)[1].split("  pull_request:", 1)[0]
        self.assertIn("branches: [ main ]", push)
        self.assertNotIn("audit/**", push)
        self.assertNotIn("friday-proposals/**", push)
        self.assertIn("concurrency:", self.workflow)
        self.assertIn("cancel-in-progress:", self.workflow)


if __name__ == "__main__":
    unittest.main()
