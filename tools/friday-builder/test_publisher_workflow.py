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

    def test_publisher_repairs_only_the_exact_failed_candidate_revision(self):
        workflow = WORKFLOW.read_text(encoding="utf-8")
        publisher = workflow.split("  publish_draft_pr:", 1)[1]

        self.assertIn('git fetch origin "$SOURCE_BRANCH"', publisher)
        self.assertIn('ACTUAL_SHA="$(git rev-parse FETCH_HEAD)"', publisher)
        self.assertIn('if [[ "$ACTUAL_SHA" != "$SOURCE_SHA" ]]', publisher)
        self.assertIn("Candidate branch advanced while Builder was working", publisher)
        self.assertIn('git push origin "$BRANCH"', publisher)
        self.assertIn("refusing to overwrite it", publisher)

    def test_publisher_reuses_existing_pr_and_explains_permission_blocker(self):
        workflow = WORKFLOW.read_text(encoding="utf-8")
        publisher = workflow.split("  publish_draft_pr:", 1)[1]

        self.assertIn('gh pr list --repo "$REPOSITORY" --head "$BRANCH" --state open', publisher)
        self.assertIn("GitHub Actions is not permitted to create or approve pull requests", publisher)
        self.assertIn("Allow GitHub Actions to create and approve pull requests", publisher)
        self.assertIn("https://github.com/$REPOSITORY/settings/actions", publisher)

    def test_publisher_setup_document_preserves_the_read_only_worker_boundary(self):
        setup = (ROOT / "docs" / "FRIDAY_TRUSTED_PUBLISHER_SETUP.md").read_text(encoding="utf-8")
        self.assertIn("Do not solve this by giving the coding worker", setup)
        self.assertIn("required checks", setup)
        self.assertIn("high-risk", setup)

    def test_only_trusted_publisher_gets_actions_write_and_dispatches_exact_sha(self):
        workflow = WORKFLOW.read_text(encoding="utf-8")
        worker = workflow.split("  build_proposal:", 1)[1].split("  publish_draft_pr:", 1)[0]
        publisher = workflow.split("  publish_draft_pr:", 1)[1]
        permissions = publisher.split("    permissions:", 1)[1].split("    steps:", 1)[0]

        self.assertIn("actions: write", permissions)
        self.assertNotIn("actions: write", worker)
        self.assertIn('gh workflow run build.yml --repo "$REPOSITORY" --ref "$BRANCH"', publisher)
        self.assertIn("gh run list --repo", publisher)
        self.assertIn("workflow_dispatch", publisher)
        self.assertIn('gh run watch "$RUN_ID" --repo "$REPOSITORY" --exit-status', publisher)
        self.assertIn("FRIDAY_PROPOSAL_SHA", publisher)
        self.assertIn("VERIFIED_SHA", publisher)
        self.assertIn("VERIFIED_CONCLUSION", publisher)
        self.assertNotIn('gh pr checks "$PR_URL" --repo "$REPOSITORY" --watch', publisher)

    def test_candidate_is_pushed_before_dispatch_and_merge_is_after_exact_sha_watch(self):
        workflow = WORKFLOW.read_text(encoding="utf-8")
        publisher = workflow.split("  publish_draft_pr:", 1)[1]
        pushed = publisher.index('git push origin "$BRANCH"')
        dispatched = publisher.index('gh workflow run build.yml --repo "$REPOSITORY" --ref "$BRANCH"')
        located = publisher.index("gh run list --repo")
        watched = publisher.index('gh run watch "$RUN_ID" --repo "$REPOSITORY" --exit-status')
        merged = publisher.index('gh pr merge "$PR_URL" --repo "$REPOSITORY" --squash --delete-branch')
        self.assertLess(pushed, dispatched)
        self.assertLess(dispatched, located)
        self.assertLess(located, watched)
        self.assertLess(watched, merged)

    def test_untrusted_worker_does_not_receive_repository_write_token(self):
        workflow = WORKFLOW.read_text(encoding="utf-8")
        worker = workflow.split("  build_proposal:", 1)[1].split("  publish_draft_pr:", 1)[0]
        self.assertIn("contents: read", worker)
        self.assertIn("actions: read", worker)
        self.assertIn("env -u GITHUB_TOKEN -u GH_TOKEN -u NVIDIA_API_KEY", worker)
        self.assertNotIn("contents: write", worker)


if __name__ == "__main__":
    unittest.main()
