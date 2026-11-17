"""Precision tests for the repository's vision-audit severity semantics."""
import importlib.util
import sys
from pathlib import Path
from types import SimpleNamespace
import unittest


ROOT = Path(__file__).resolve().parents[2]
AUDIT_PATH = ROOT / ".ralph" / "audit.py"
SPEC = importlib.util.spec_from_file_location("friday_vision_audit_under_test", AUDIT_PATH)
AUDIT = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = AUDIT
SPEC.loader.exec_module(AUDIT)


class VisionAuditSeverityTest(unittest.TestCase):
    def test_closed_enum_is_an_observation_not_an_automatic_vision_violation(self):
        source = "enum class Mode { IDLE, ACTIVE, }"
        file = SimpleNamespace(
            path="Example.kt",
            text=source,
            added={1: source},
        )
        diff = SimpleNamespace(files=[file])
        vision = SimpleNamespace(item={"predefined": "S13 item 3"})

        findings = AUDIT.check_closed_type(diff, None, vision)

        self.assertEqual(1, len(findings))
        self.assertEqual("closed_type", findings[0].check)
        self.assertEqual("observation", findings[0].severity)
        self.assertIn("IDLE, ACTIVE", findings[0].detail)

    def test_function_that_maps_into_multiple_fixed_outcomes_remains_a_deviation(self):
        source = """enum class Mode { IDLE, ACTIVE, }
fun classify(input: String): Mode = when (input) {
    "idle" -> Mode.IDLE
    else -> Mode.ACTIVE
}
"""
        file = SimpleNamespace(
            path="Example.kt",
            text=source,
            added={
                1: "enum class Mode { IDLE, ACTIVE, }",
                2: "fun classify(input: String): Mode = when (input) {",
                3: '    "idle" -> Mode.IDLE',
                4: "    else -> Mode.ACTIVE",
                5: "}",
            },
        )
        diff = SimpleNamespace(files=[file])
        vision = SimpleNamespace(item={"fixed_role": "S13 item 2"})

        findings = AUDIT.check_fixed_classification(diff, None, vision)

        self.assertEqual(1, len(findings))
        self.assertEqual("fixed_classification", findings[0].check)
        self.assertEqual("deviation", findings[0].severity)


if __name__ == "__main__":
    unittest.main()
