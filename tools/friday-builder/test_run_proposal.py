from pathlib import Path
import unittest

SCRIPT = Path(__file__).with_name("run_proposal.sh")
SOURCE = SCRIPT.read_text(encoding="utf-8")


class BuilderFreeModelFallbackTests(unittest.TestCase):
    def test_tries_every_available_free_model_in_order(self):
        self.assertIn("AVAILABLE_MODELS=()", SOURCE)
        self.assertIn('AVAILABLE_MODELS+=("$candidate")', SOURCE)
        self.assertIn('for candidate in "${AVAILABLE_MODELS[@]}"; do', SOURCE)

    def test_each_model_has_a_bounded_configurable_timeout(self):
        self.assertIn("FRIDAY_BUILDER_MODEL_ATTEMPT_TIMEOUT_SECONDS:-300", SOURCE)
        self.assertIn('timeout -k 15 "$MODEL_ATTEMPT_TIMEOUT_SECONDS" opencode run', SOURCE)
        self.assertNotIn("timeout -k 15 3000 opencode run", SOURCE)

    def test_failed_or_empty_attempts_are_reset_before_fallback(self):
        self.assertGreaterEqual(SOURCE.count('git reset --hard "$BASE_SHA"'), 2)
        self.assertGreaterEqual(SOURCE.count("git clean -fdx"), 2)
        self.assertIn("trying the next available free lane", SOURCE)

    def test_worker_requires_a_real_source_change_before_publishing(self):
        self.assertIn("git status --porcelain --untracked-files=all", SOURCE)
        self.assertIn('if [[ "$MODEL_SUCCEEDED" -ne 1 ]]', SOURCE)
        self.assertIn("test -s proposal.patch", SOURCE)

    def test_worker_still_has_no_repository_write_credentials(self):
        self.assertIn("You have no repository-write credential.", SOURCE)
        self.assertIn("repository-write credentials are absent", SOURCE)


if __name__ == "__main__":
    unittest.main()