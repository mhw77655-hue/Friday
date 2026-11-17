from __future__ import annotations

import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
WORKFLOW = ROOT / ".github" / "workflows" / "friday-builder-proposal.yml"


class PublisherWorkflowTests(unittest.TestCase):
    def test_trusted_validator_is_copied_before_untrusted_patch_is_applied(self):
        workflow = WORKFLOW.read_text(encoding="utf-8")
        publisher = workflow.split("  publish_draft_pr:", 1)[1]

        copy_validator = 'install -m 600 tools/friday-builder/validate_patch_paths.py'
        apply_patch = 'git apply --index proposal-artifact/proposal.patch'
        execute_validator = 'python3 "$RUNNER_TEMP/friday-validate-patch-paths.py" HEAD'
        execute_workspace_validator = 'python3 tools/friday-builder/validate_patch_paths.py HEAD'

        self.assertIn(copy_validator, publisher)
        self.assertIn(apply_patch, publisher)
        self.assertIn(execute_validator, publisher)
        self.assertLess(publisher.index(copy_validator), publisher.index(apply_patch))
        self.assertLess(publisher.index(apply_patch), publisher.index(execute_validator))
        self.assertNotIn(execute_workspace_validator, publisher)

    def test_untrusted_worker_does_not_receive_repository_write_token(self):
        workflow = WORKFLOW.read_text(encoding="utf-8")
        worker = workflow.split("  build_proposal:", 1)[1].split("  publish_draft_pr:", 1)[0]
        self.assertIn("contents: read", worker)
        self.assertIn("actions: read", worker)
        self.assertIn("env -u GITHUB_TOKEN -u GH_TOKEN -u NVIDIA_API_KEY", worker)
        self.assertNotIn("contents: write", worker)


if __name__ == "__main__":
    unittest.main()
