from __future__ import annotations
import unittest
from validate_patch_paths import is_protected


class ValidatePatchPathsTests(unittest.TestCase):
    def test_rejects_control_plane_and_secrets(self):
        for path in (".github/workflows/a.yml", ".ralph/VISION.md", "tools/friday-builder/a.py",
                     "development-requests/inbox/x.json", ".env", "signing/key.jks", "../escape"):
            with self.subTest(path=path):
                self.assertTrue(is_protected(path))

    def test_allows_application_source_and_tests(self):
        self.assertFalse(is_protected("mobile/app/src/main/java/com/example/Friday.kt"))
        self.assertFalse(is_protected("mobile/app/src/test/java/com/example/FridayTest.kt"))


if __name__ == "__main__":
    unittest.main()
