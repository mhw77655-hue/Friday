from __future__ import annotations

import subprocess
import tempfile
import unittest
from pathlib import Path

from validate_patch_paths import inspect_patch, is_protected


class ValidatePatchPathsTests(unittest.TestCase):
    def test_rejects_control_plane_and_secrets(self):
        for path in (
            ".github/workflows/a.yml",
            ".ralph/VISION.md",
            "tools/friday-builder/a.py",
            "development-requests/inbox/x.json",
            ".env",
            "signing/key.jks",
            "signing/private.pem",
            "signing/release.key",
            "../escape",
        ):
            with self.subTest(path=path):
                self.assertTrue(is_protected(path))

    def test_allows_application_source_and_tests(self):
        self.assertFalse(is_protected("mobile/app/src/main/java/com/example/Friday.kt"))
        self.assertFalse(is_protected("mobile/app/src/test/java/com/example/FridayTest.kt"))

    def test_rejects_symlink_even_when_path_is_allowed(self):
        with tempfile.TemporaryDirectory() as directory:
            repo = Path(directory)
            subprocess.run(["git", "init", "-q"], cwd=repo, check=True)
            subprocess.run(["git", "config", "user.name", "test"], cwd=repo, check=True)
            subprocess.run(["git", "config", "user.email", "test@example.invalid"], cwd=repo, check=True)
            (repo / "base.txt").write_text("base", encoding="utf-8")
            subprocess.run(["git", "add", "base.txt"], cwd=repo, check=True)
            subprocess.run(["git", "commit", "-qm", "base"], cwd=repo, check=True)

            (repo / "outside.txt").write_text("outside", encoding="utf-8")
            (repo / "mobile").mkdir()
            (repo / "mobile" / "escape.txt").symlink_to("../outside.txt")
            subprocess.run(["git", "add", "mobile/escape.txt"], cwd=repo, check=True)

            paths, protected, unsafe = inspect_patch("HEAD", repo)

            self.assertEqual(paths, ["mobile/escape.txt"])
            self.assertEqual(protected, [])
            self.assertEqual(unsafe, ["mobile/escape.txt (symbolic link)"])

    def test_checks_both_sides_of_rename(self):
        with tempfile.TemporaryDirectory() as directory:
            repo = Path(directory)
            subprocess.run(["git", "init", "-q"], cwd=repo, check=True)
            subprocess.run(["git", "config", "user.name", "test"], cwd=repo, check=True)
            subprocess.run(["git", "config", "user.email", "test@example.invalid"], cwd=repo, check=True)
            (repo / ".github").mkdir()
            (repo / ".github" / "workflow.yml").write_text("name: old", encoding="utf-8")
            subprocess.run(["git", "add", ".github/workflow.yml"], cwd=repo, check=True)
            subprocess.run(["git", "commit", "-qm", "base"], cwd=repo, check=True)
            (repo / ".github" / "workflow.yml").rename(repo / "workflow-notes.txt")
            subprocess.run(["git", "add", "-A"], cwd=repo, check=True)

            paths, protected, _ = inspect_patch("HEAD", repo)

            self.assertIn(".github/workflow.yml", paths)
            self.assertIn("workflow-notes.txt", paths)
            self.assertIn(".github/workflow.yml", protected)


if __name__ == "__main__":
    unittest.main()
