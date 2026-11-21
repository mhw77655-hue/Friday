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

    def test_non_object_policy_root_is_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "policy.json"
            path.write_text("[]", encoding="utf-8")
            with self.assertRaisesRegex(ValueError, "root must be a JSON object"):
                MODULE.load_policy(path)

    def test_malformed_nested_policy_shapes_fail_closed_without_crashing(self):
        malformed_values = [None, [], "", 0, {"unexpected": "shape"}]
        for field in ("authority", "capability_evidence", "capabilities"):
            for value in malformed_values:
                policy = dict(self.policy)
                policy[field] = value
                errors = MODULE.verify(policy, root=Path("/definitely/missing"))
                self.assertTrue(errors, (field, value))
        for value in malformed_values:
            policy = dict(self.policy)
            policy["authority"] = {
                "default_for_unlisted_sources": "non_authoritative",
                "precedence": value,
                "instruction_discovery_globs": ["**/*.md"],
            }
            self.assertTrue(MODULE.verify(policy, root=Path("/definitely/missing")))

    def test_malformed_capability_evidence_is_rejected(self):
        for evidence in (None, [], "", 0):
            capability = {
                "id": "demo",
                "level": "production_wired",
                "evidence": evidence,
            }
            errors = MODULE.validate_capability(capability, self.policy)
            self.assertTrue(errors)
            self.assertIn("evidence must be a JSON object", errors[0])

        malformed_policy = dict(self.policy)
        malformed_policy["capability_evidence"] = {"required_evidence": []}
        errors = MODULE.validate_capability(
            {"id": "demo", "level": "production_wired", "evidence": {}},
            malformed_policy,
        )
        self.assertTrue(errors)
        self.assertIn("required_evidence must be a JSON object", errors[0])

    def test_authority_lookup_fails_closed_on_malformed_registry(self):
        for policy in (None, [], {}, {"authority": []}, {"authority": {"precedence": [None, "VISION.md"]}}):
            self.assertFalse(MODULE.is_authoritative(".ralph/VISION.md", policy))

    def test_unknown_evidence_level_fails_closed(self):
        errors = MODULE.validate_capability({"id": "demo", "level": "magic"}, self.policy)
        self.assertTrue(errors)


if __name__ == "__main__":
    unittest.main()
