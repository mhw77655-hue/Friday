"""
Phase 0 exit test (Blueprint Section 4): a deliberate out-of-manifest write
attempt is rejected before it reaches disk, and the rejection is logged.

Run: python manifest/test_protected_paths.py
"""

import json
import os
import tempfile
from pathlib import Path

import protected_paths as pp


def test_out_of_manifest_write_is_denied_and_logged():
    with tempfile.TemporaryDirectory() as tmp:
        repo_base = tmp
        forbidden = os.path.join(repo_base, "some_random_unlisted_dir", "evil.kt")
        Path(os.path.dirname(forbidden)).mkdir(parents=True, exist_ok=True)

        result = pp.check_path(forbidden, "write", repo="jarvis", repo_base=repo_base)

        assert result.allowed is False
        assert not os.path.exists(forbidden)
        assert pp.LOG_PATH.exists()
        with open(pp.LOG_PATH) as f:
            last = json.loads(f.readlines()[-1])
        assert last["allowed"] is False
        assert last["action"] == "write"
        print("PASS -- denied write:", last)


def test_protected_path_write_requires_approval():
    with tempfile.TemporaryDirectory() as tmp:
        repo_base = tmp
        manifest_dir = os.path.join(repo_base, "manifest")
        Path(manifest_dir).mkdir(parents=True, exist_ok=True)
        target = os.path.join(manifest_dir, "workspace_manifest.yaml")

        result = pp.check_path(target, "write", repo="jarvis", repo_base=repo_base)

        assert result.allowed is False
        assert "Tier 2" in result.reason
        print("PASS -- protected path blocked:", result.reason)


def test_manifest_root_write_is_allowed():
    with tempfile.TemporaryDirectory() as tmp:
        repo_base = tmp
        allowed_dir = os.path.join(repo_base, "mobile", "app")
        Path(allowed_dir).mkdir(parents=True, exist_ok=True)
        target = os.path.join(allowed_dir, "NewFile.kt")

        result = pp.check_path(target, "write", repo="jarvis", repo_base=repo_base)

        assert result.allowed is True
        print("PASS -- in-manifest write allowed:", result.path)


if __name__ == "__main__":
    test_out_of_manifest_write_is_denied_and_logged()
    test_protected_path_write_requires_approval()
    test_manifest_root_write_is_allowed()
    print("\nAll Phase 0 exit-criteria checks passed.")
