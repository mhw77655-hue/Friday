from pathlib import Path
import unittest

SCRIPT = Path(__file__).with_name("run_proposal.sh")
SOURCE = SCRIPT.read_text(encoding="utf-8")


class BuilderLocalFallbackTests(unittest.TestCase):
    def test_hosted_lanes_are_bounded_and_fall_through(self):
        self.assertIn("FRIDAY_BUILDER_MODEL_ATTEMPT_TIMEOUT_SECONDS:-300", SOURCE)
        self.assertIn('timeout -k 15 "$MODEL_ATTEMPT_TIMEOUT_SECONDS" opencode run', SOURCE)
        self.assertIn("trying the next available free lane", SOURCE)

    def test_no_hosted_lane_does_not_prevent_local_fallback(self):
        self.assertIn("the local no-API fallback will still be attempted", SOURCE)
        self.assertIn("Hosted free lanes exhausted; trying the local Ollama fallback", SOURCE)

    def test_local_fallback_is_open_weight_and_tool_capable(self):
        self.assertIn("ollama pull qwen3:4b-instruct", SOURCE)
        self.assertIn('LOCAL_MODEL="ollama/qwen3:4b-instruct"', SOURCE)
        self.assertIn("OLLAMA_GO_TEMPLATE=false", SOURCE)

    def test_local_fallback_has_bounded_resources_and_timeouts(self):
        self.assertIn("OLLAMA_CONTEXT_LENGTH=32768", SOURCE)
        self.assertIn("OLLAMA_NUM_PARALLEL=1", SOURCE)
        self.assertIn("timeout -k 15 600 ollama pull", SOURCE)
        self.assertIn("timeout -k 15 900 opencode run", SOURCE)

    def test_only_a_real_source_change_is_accepted(self):
        self.assertIn("git status --porcelain --untracked-files=all", SOURCE)
        self.assertIn('if [[ "$MODEL_SUCCEEDED" -ne 1 ]]', SOURCE)
        self.assertIn("test -s proposal.patch", SOURCE)


if __name__ == "__main__":
    unittest.main()