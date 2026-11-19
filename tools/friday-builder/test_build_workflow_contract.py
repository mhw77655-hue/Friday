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

    def test_candidate_branches_do_not_run_duplicate_push_and_pull_request_builds(self):
        push = self.workflow.split("  push:", 1)[1].split("  pull_request:", 1)[0]
        self.assertIn("branches: [ main ]", push)
        self.assertNotIn("audit/**", push)
        self.assertNotIn("friday-proposals/**", push)
        self.assertIn("concurrency:", self.workflow)
        self.assertIn("cancel-in-progress:", self.workflow)


if __name__ == "__main__":
    unittest.main()
