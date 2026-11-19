"""Regression guards for keeping raw owner archives out of Friday's public repo."""
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


class PrivateArchivePolicyTests(unittest.TestCase):
    def test_known_raw_archives_are_ignored(self):
        ignored = (ROOT / ".gitignore").read_text(encoding="utf-8")
        self.assertIn("New Folder chats.zip", ignored)
        self.assertIn("New folder.zip", ignored)
        self.assertIn("*.owner-private.zip", ignored)

    def test_public_vision_points_to_curated_owner_words_not_raw_archives(self):
        vision = (ROOT / ".ralph/VISION.md").read_text(encoding="utf-8")
        self.assertIn(".ralph/OWNER_WORDS.md", vision)
        self.assertIn("Raw owner conversation and device-report archives are deliberately excluded", vision)
        self.assertNotIn("New Folder chats.zip", vision)
        self.assertNotIn("New folder.zip", vision)

    def test_archive_review_contains_only_sanitized_findings(self):
        review = (ROOT / "docs/FRIDAY_SOURCE_ARCHIVE_REVIEW.md").read_text(encoding="utf-8")
        self.assertIn("Raw owner conversation and device-report archives are intentionally not stored", review)
        self.assertNotIn("RMX3472", review)
        self.assertNotIn("voice PID", review)
        self.assertNotIn("app PID", review)

    def test_one_time_history_purge_covers_both_archive_paths_and_all_refs(self):
        workflow = (ROOT / ".github/workflows/purge-private-archives.yml").read_text(encoding="utf-8")
        self.assertIn("--sensitive-data-removal", workflow)
        self.assertIn("pull-requests: read", workflow)
        self.assertIn("workflow_dispatch:", workflow)
        self.assertNotRegex(workflow, r"(?m)^  push:")
        self.assertIn('--path "New Folder chats.zip"', workflow)
        self.assertIn('--path "New folder.zip"', workflow)
        self.assertIn("git push --force --mirror origin", workflow)
        self.assertIn("gh pr list --repo", workflow)
        self.assertIn("affected-pull-requests.txt", workflow)
        self.assertIn(".github/PRIVATE_ARCHIVE_PURGE_PENDING", workflow)


if __name__ == "__main__":
    unittest.main()
