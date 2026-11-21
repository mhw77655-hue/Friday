"""Adversarial unit tests for the repository's own vision-audit engine."""
import importlib.util
import os
import tempfile
import unittest
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import patch
import sys

ROOT = Path(__file__).resolve().parents[2]
AUDIT_PATH = ROOT / ".ralph" / "audit.py"
SPEC = importlib.util.spec_from_file_location("friday_audit_core_under_test", AUDIT_PATH)
AUDIT = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = AUDIT
SPEC.loader.exec_module(AUDIT)


class VisionAuditCoreTests(unittest.TestCase):
    def test_vision_slices_only_the_requested_section(self):
        raw = "## 4. Rule\nliquid rule\n## 12. Disclosures\nknown issue\n## 13. Checklist\nquestion?"
        self.assertEqual("liquid rule", AUDIT.Vision._slice(raw, "4"))
        self.assertEqual("question?", AUDIT.Vision._slice(raw, "13"))

    def test_vision_checklist_rejoins_wrapped_bullets_and_ignores_non_questions(self):
        raw = """## 13. Checklist
- Is this a property of the whole system
  rather than a local implementation?
- This is a heading
- Did this create a fixed, named connection?
"""
        self.assertEqual([
            "Is this a property of the whole system rather than a local implementation?",
            "Did this create a fixed, named connection?",
        ], AUDIT.Vision._checklist(raw))

    def test_vision_constructor_resolves_distinctive_checklist_phrases(self):
        questions = [
            "Is this a property of the whole system?",
            "Did this turn emergent, per-task behavior into a fixed role?",
            "Did this add a predefined list of inputs?",
            "Did this create a fixed, named connection?",
            "Did this create one permanent job?",
            "Did this assume an implicit hardware ceiling?",
            "Did this rename the old process?",
            "Does this describe a representation as if it were what the underlying thing is?",
        ]
        with tempfile.NamedTemporaryFile("w", encoding="utf-8", delete=False) as fh:
            fh.write("## 4. Rule\nThe mechanism remains open.\n## 13. Checklist\n")
            fh.write("\n".join("- " + q for q in questions))
            fh.write("\n")
            path = fh.name
        try:
            vision = AUDIT.Vision(path)
            self.assertEqual(set(AUDIT.S13_PHRASES), set(vision.item))
            self.assertTrue(all(value is not None for value in vision.item.values()))
        finally:
            os.unlink(path)

    def test_enum_parser_strips_comments_and_constructor_arguments(self):
        source = """enum class Mode {
            FIRST,
            /** comment with FALSE, */
            SECOND(false),
            THIRD,
        }"""
        self.assertEqual(["FIRST", "SECOND", "THIRD"], AUDIT.enum_members(source, "Mode"))
        self.assertEqual([], AUDIT.enum_members(source, "Missing"))

    def test_function_body_returns_only_the_requested_braced_function(self):
        source = "fun before() = 1\nfun target() {\n  if (true) { return 2 }\n}\nfun after() = 3"
        self.assertEqual("fun target() {\n  if (true) { return 2 }\n}", AUDIT.function_body(source, "target", 2))
        self.assertEqual("", AUDIT.function_body(source, "target", 0))
        self.assertEqual("", AUDIT.function_body(source, "target", 99))

    def test_outcome_members_ignores_comments_and_finds_return_and_when_results(self):
        body = """fun choose(x: Int): Mode {
            // return FIRST
            if (x < 0) return Mode.FIRST
            return when (x) {
                0 -> SECOND // comment
                else -> {
                    THIRD
                }
            }
        }"""
        found, where = AUDIT.outcome_members(body, ["FIRST", "SECOND", "THIRD", "UNUSED"])
        self.assertEqual({"FIRST", "SECOND"}, found)
        self.assertIn("return Mode.FIRST", where["FIRST"])
        self.assertIn("0 -> SECOND", where["SECOND"])

    def test_numeric_constants_exclude_data_defaults_and_accept_named_thresholds(self):
        cf = SimpleNamespace(added={
            1: "private const val MAX_RETRIES: Int = 4",
            2: "val WINDOW_MS: Long = 500L",
            3: "val decayRate: Double = 0.2",
            4: "val ordinary = 9",
        })
        values = AUDIT._numeric_constants(cf)
        self.assertEqual(["MAX_RETRIES", "WINDOW_MS"], [v[0] for v in values])

    def test_constructor_parameter_lines_do_not_capture_function_parameters(self):
        source = "class Engine(\n    val input: Input,\n    val output: Output\n) {\n    fun verify(\n        baseline: ReplayResult,\n        current: ReplayResult\n    ) = true\n}"
        cf = SimpleNamespace(text=source, added={1: "class Engine("})
        self.assertEqual({1, 2, 3, 4}, AUDIT.type_param_lines(cf))

    def test_module_literal_sets_reads_multiline_dictionaries_and_lists(self):
        lines = [
            "RECIPES = {",
            '    "safe_retry": "bounded",',
            '    "fallback": "verified",',
            "}",
            "OTHER = [",
            '    "alpha",',
            '    "beta",',
            "]",
        ]
        self.assertEqual([
            ("RECIPES", 1, ["safe_retry", "fallback"]),
            ("OTHER", 5, ["alpha", "beta"]),
        ], AUDIT.module_literal_sets(lines))

    def test_repo_index_finds_class_interface_and_object(self):
        with tempfile.TemporaryDirectory() as root:
            Path(root, "Types.kt").write_text("class Engine\ninterface Port\nobject Registry\n", encoding="utf-8")
            repo = AUDIT.Repo(scan_from=root)
            self.assertEqual("class", repo.declared_type("Engine")[0])
            self.assertEqual("interface", repo.declared_type("Port")[0])
            self.assertEqual("object", repo.declared_type("Registry")[0])
            self.assertIsNone(repo.declared_type("Missing"))
            self.assertTrue(repo.file_of("Engine").endswith("Types.kt"))

    def test_diff_from_paths_marks_every_line_as_added(self):
        with tempfile.NamedTemporaryFile("w", encoding="utf-8", suffix=".kt", dir=ROOT, delete=False) as fh:
            fh.write("class NewEngine\nfun run() = true\n")
            absolute = Path(fh.name)
        try:
            relative = absolute.relative_to(ROOT).as_posix()
            diff = AUDIT.Diff.from_paths([relative])
            self.assertEqual(1, len(diff.files))
            self.assertEqual({1, 2}, set(diff.files[0].added))
            self.assertTrue(diff.files[0].created)
        finally:
            absolute.unlink(missing_ok=True)

    def test_model_gate_never_calls_when_disabled_and_counts_successful_attempts(self):
        disabled = AUDIT.ModelGate(False, ["fake"])
        with patch.object(AUDIT.subprocess, "run") as run:
            self.assertIsNone(disabled.ask("prompt"))
            run.assert_not_called()
        enabled = AUDIT.ModelGate(True, ["fake"])
        with patch.object(AUDIT.subprocess, "run", return_value=SimpleNamespace(stdout="  yes  ")) as run:
            self.assertEqual("yes", enabled.ask("prompt"))
            run.assert_called_once()
        self.assertEqual(1, enabled.calls)

    def test_model_gate_fails_closed_on_command_error(self):
        gate = AUDIT.ModelGate(True, ["missing-command"])
        with patch.object(AUDIT.subprocess, "run", side_effect=OSError("not found")):
            self.assertIsNone(gate.ask("prompt"))
        self.assertEqual(1, gate.calls)

    def test_build_patch_with_no_recipe_is_explicitly_empty(self):
        diff, notes = AUDIT.build_patch([])
        self.assertIsNone(diff)
        self.assertTrue(notes)
        self.assertIn("no patch", " ".join(notes).lower())


if __name__ == "__main__":
    unittest.main()
