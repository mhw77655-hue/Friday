from __future__ import annotations

import unittest

from validate_patch_paths import is_protected


class ValidatePatchPathsTests(unittest.TestCase):
    def test_rejects_worker_control_plane(self) -> None:
        for path in (
            ".github/workflows/new.yml",
            ".ralph/prd.json",
            "tools/friday-builder/validate_request.py",
            "development-requests/inbox/task.json",
        ):
            with self.subTest(path=path):
                self.assertTrue(is_protected(path))

    def test_rejects_credentials_and_binary_release_artifacts(self) -> None:
        for path in (
            ".env",
            "config/.env.production",
            "signing/release.jks",
            "release/app.apk",
            "mobile/app/build/outputs/app.aab",
            "local.properties",
            ".gitmodules",
        ):
            with self.subTest(path=path):
                self.assertTrue(is_protected(path))

    def test_rejects_unsafe_path_components(self) -> None:
        for path in ("../outside.txt", "mobile/../../outside.txt", "/tmp/payload"):
            with self.subTest(path=path):
                self.assertTrue(is_protected(path))

    def test_allows_normal_source_and_documentation(self) -> None:
        for path in (
            "mobile/app/src/main/java/com/example/Friday.kt",
            "mobile/app/src/test/java/com/example/FridayTest.kt",
            "docs/FRIDAY_BUILD_PROGRESS.md",
        ):
            with self.subTest(path=path):
                self.assertFalse(is_protected(path))


if __name__ == "__main__":
    unittest.main()
