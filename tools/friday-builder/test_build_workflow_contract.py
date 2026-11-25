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
            ["debug-apk", "android-emulator", "full-source-audit", "report-failure", "verified-debug-apk"],
        )
        self.assertEqual(len(ids), len(set(ids)))

    def test_apk_bundled_delivery_plan_matches_canonical_source(self):
        canonical = ROOT / ".ralph" / "FRIDAY_DELIVERY_PLAN.md"
        bundled = ROOT / "mobile" / "app" / "src" / "main" / "assets" / "friday" / "FRIDAY_DELIVERY_PLAN.md"
        self.assertEqual(canonical.read_bytes(), bundled.read_bytes())
        self.assertIn("Verify APK-bundled delivery plan matches canonical source", self.workflow)

    def test_installable_apk_is_promoted_only_after_all_required_gates(self):
        self.assertIn("name: unverified-debug-apk-candidate", self.workflow)
        gate = self.workflow.split("  verified-debug-apk:", 1)[1]
        for job in ("debug-apk", "android-emulator", "full-source-audit"):
            self.assertIn(f"- {job}", gate)
        self.assertIn("name: friday-verified-debug-apk", gate)
        self.assertIn("unzip -t", gate)
        self.assertIn("sha256sum", gate)

    def test_release_signing_smoke_runs_on_every_full_validation(self):
        debug = self.workflow.split("  debug-apk:", 1)[1].split("  android-emulator:", 1)[0]
        self.assertEqual(debug.count("Build and verify release variant with a disposable CI key"), 1)
        smoke = debug.split("      - name: Build and verify release variant with a disposable CI key", 1)[1]
        smoke = smoke.split("      # Run tests even when APK assembly fails.", 1)[0]
        self.assertIn("friday-ci-smoke.p12", smoke)
        self.assertIn("keytool -genkeypair", smoke)
        self.assertIn("apksigner\" verify --verbose", smoke)
        self.assertNotIn("        if:", smoke)
        self.assertNotIn("Detect release-signing changes", debug)

    def test_compiled_apk_must_contain_a_launcher_icon(self):
        debug = self.workflow.split("  debug-apk:", 1)[1].split("  android-emulator:", 1)[0]
        self.assertEqual(debug.count("Verify Friday launcher icon is packaged"), 1)
        self.assertIn('"$ANDROID_HOME/build-tools/35.0.0/aapt" dump badging mobile/app/build/outputs/apk/debug/app-debug.apk', debug)
        self.assertIn("application: label='Friday' icon='[^']+'", debug)
        manifest = (ROOT / "mobile" / "app" / "src" / "main" / "AndroidManifest.xml").read_text(encoding="utf-8")
        self.assertIn('android:icon="@mipmap/ic_launcher"', manifest)

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
        self.assertIn("-memory 3072 -cores 2", self.workflow)

    def test_harness_failure_does_not_short_circuit_apk_validation_or_release(self):
        self.assertIn("id: harness-contracts", self.workflow)
        harness = self.workflow.split("      - name: Test Android harness and build contracts", 1)[1]
        harness = harness.split("      - name: Measure Python line and branch coverage", 1)[0]
        self.assertIn("continue-on-error: true", harness)
        enforcement = self.workflow.split("      - name: Enforce Android harness and build contract results", 1)[1]
        enforcement = enforcement.split("      - name: Summarize test results", 1)[0]
        self.assertIn("steps.harness-contracts.outcome", enforcement)
        self.assertIn("exit 1", enforcement)

    def test_manual_workflow_dispatch_is_available_for_recovery(self):
        self.assertIn("workflow_dispatch:", self.workflow)


    def test_active_pull_request_validation_is_not_cancelled_by_new_commits(self):
        self.assertIn("on:\n  push:\n    branches: [ main ]", self.workflow)
        self.assertIn("pull_request:", self.workflow)
        self.assertIn("concurrency:", self.workflow)
        self.assertIn("group: friday-build-${{ github.workflow }}-${{ github.event.pull_request.number || github.ref }}", self.workflow)
        # Preserve the running emulator/build evidence. New commits replace
        # obsolete pending candidates without cancelling the active run.
        self.assertIn("cancel-in-progress: false", self.workflow)
        self.assertNotIn("cancel-in-progress: ${{ github.event_name == 'pull_request' }}", self.workflow)
        self.assertIn("workflow_dispatch:", self.workflow)


if __name__ == "__main__":
    unittest.main()
