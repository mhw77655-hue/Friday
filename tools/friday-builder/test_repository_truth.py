import importlib.util
import json
import tempfile
import unittest
from pathlib import Path

SCRIPT = Path(__file__).resolve().parents[2] / "scripts" / "verify_repository_truth.py"
SPEC = importlib.util.spec_from_file_location("repository_truth", SCRIPT)
MODULE = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
SPEC.loader.exec_module(MODULE)


class RepositoryTruthTests(unittest.TestCase):
    def setUp(self):
        self.policy = {
            "schema_version": 1,
            "authority": {
                "default_for_unlisted_sources": "non_authoritative",
                "precedence": [
                    {"path": ".ralph/VISION.md", "role": "owner_vision"},
                    {"path": ".ralph/FRIDAY_DELIVERY_PLAN.md", "role": "sole_delivery_authority"},
                ],
                "instruction_discovery_globs": ["**/PLAN*.md"],
            },
            "capability_evidence": {
                "manifest_policy": "claims_only",
                "required_evidence": {
                    "production_wired": ["production_composition"],
                    "integration_tested": ["production_composition", "integration_test"],
                    "device_verified": ["production_composition", "device_test", "device_identity", "observed_postcondition"],
                    "released": ["production_composition", "integration_test", "device_test", "release_artifact", "artifact_digest", "signing_identity"],
                },
            },
        }

    def test_unlisted_historical_plan_is_not_authoritative(self):
        self.assertFalse(MODULE.is_authoritative("docs/OLD_ROADMAP.md", self.policy))

    def test_only_exact_registered_source_is_authoritative(self):
        self.assertTrue(MODULE.is_authoritative(".ralph/VISION.md", self.policy))
        self.assertFalse(MODULE.is_authoritative("docs/VISION_OLD.md", self.policy))

    def test_manifest_claim_cannot_promote_capability(self):
        errors = MODULE.validate_capability(
            {"id": "demo", "level": "declared", "manifest": True}, self.policy
        )
        self.assertEqual(errors, [])

    def test_production_wired_requires_composition_evidence(self):
        errors = MODULE.validate_capability(
            {"id": "demo", "level": "production_wired", "evidence": {}}, self.policy
        )
        self.assertIn("demo: production_wired requires evidence.production_composition", errors)

    def test_released_requires_full_provenance(self):
        errors = MODULE.validate_capability(
            {"id": "demo", "level": "released", "evidence": {"production_composition": "x"}}, self.policy
        )
        self.assertEqual(len(errors), 5)

    def test_unknown_evidence_level_fails_closed(self):
        errors = MODULE.validate_capability({"id": "demo", "level": "magic"}, self.policy)
        self.assertTrue(errors)


if __name__ == "__main__":
    unittest.main()
