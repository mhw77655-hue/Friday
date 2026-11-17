from __future__ import annotations

import json
import unittest

from validate_request import RequestValidationError, validate_request


AGGREGATE_MARKER = "FRIDAY_AGGREGATE_RUNTIME_EVIDENCE_JSON (content-free; no personal content): "


def valid_aggregate_snapshot():
    return {
        "schema_version": 1,
        "observed_at_ms": 1794916800000,
        "privacy_scope": "aggregate_only_no_content",
        "runtime_readiness": "Ready",
        "identity_record_present": True,
        "experience_count": 12,
        "association_count": 29,
        "plan_status": "PARTIAL",
        "plan_step_status_counts": {"COMPLETED": 2, "SKIPPED": 1, "BLOCKED": 3},
        "capability_stage": 2,
        "available_capability_keys": ["VOICE_OUTPUT", "LOCAL_MEMORY"],
        "missing_capability_keys": ["SEMANTIC_VIDEO"],
        "symbiotic_mode": "BALANCED",
        "symbiotic_context_token_budget": 2048,
        "symbiotic_max_concurrent_expensive_tasks": 1,
        "symbiotic_memory_reserve_mb": 256,
        "self_study": {
            "source_revision": "abc123",
            "source_file_count": 10,
            "source_symbol_count": 20,
            "source_package_count": 3,
            "source_dependency_edge_count": 9,
            "graph_node_count": 12,
            "graph_edge_count": 14,
            "reachable_node_count": 11,
            "unreachable_node_count": 1,
            "missing_invariant_count": 2,
            "declared_missing_capability_keys": ["SEMANTIC_VIDEO"],
            "known_limitation_keys": ["MODEL_FREE_SEED"],
            "host_samples": 4,
            "minimum_available_memory_mb": 512,
            "average_cpu_load_ema_percent": 22.5,
            "thermal_pressure_samples": 0,
            "low_memory_samples": 1,
            "additional_memory_reserve_mb": 256,
            "maximum_observed_process_pss_mb": 800,
            "minimum_battery_percent": 35,
            "liquid_generation": 7,
            "liquid_prediction_error": 0.15,
            "organ_strategy_keys": ["BALANCED"],
            "organ_failure_counts": {"VOICE": 2},
            "next_focus_count": 3,
        },
    }


def valid_request():
    return {
        "schema_version": 1, "request_id": "phone-test-001",
        "created_at": "2026-11-16T12:00:00Z", "source_revision": "main",
        "intended_outcome": "Fix one verified defect in Friday.",
        "evidence": ["A bounded device snapshot was explicitly approved."],
        "acceptance_criteria": ["A regression test is added.", "Build and unit tests pass."],
        "risks": ["The patch is untrusted until reviewed."],
        "rollback_expectation": "Close the draft PR and discard the branch.",
        "unresolved_questions": ["Does the target phone reproduce the issue?"],
        "privacy_review": {"status": "approved", "reviewed_by": "device-owner", "reviewed_at": "2026-11-16T12:00:00Z"}
    }


def enable_aggregate_evidence(data):
    data["evidence"].append(AGGREGATE_MARKER + json.dumps(valid_aggregate_snapshot(), separators=(",", ":")))
    data["privacy_review"]["aggregate_runtime_evidence_enabled"] = True
    data["privacy_review"]["aggregate_runtime_evidence_scope"] = "counts_statuses_capability_keys_resource_trends_only"
    return data


class ValidateRequestTests(unittest.TestCase):
    def test_accepts_bounded_request(self):
        self.assertEqual(validate_request(valid_request())["request_id"], "phone-test-001")

    def test_rejects_unreviewed_request(self):
        data = valid_request()
        data["privacy_review"]["status"] = "pending"
        with self.assertRaisesRegex(RequestValidationError, "must be approved"):
            validate_request(data)

    def test_rejects_private_conversation_field(self):
        data = valid_request()
        data["conversation_transcript"] = "private"
        with self.assertRaisesRegex(RequestValidationError, "unsupported fields"):
            validate_request(data)

    def test_rejects_credential(self):
        data = valid_request()
        data["evidence"].append("github_pat_" + "A" * 40)
        with self.assertRaisesRegex(RequestValidationError, "credential"):
            validate_request(data)

    def test_accepts_content_free_aggregate_runtime_evidence(self):
        data = enable_aggregate_evidence(valid_request())

        validated = validate_request(data)

        self.assertTrue(validated["privacy_review"]["aggregate_runtime_evidence_enabled"])
        self.assertEqual(
            validated["privacy_review"]["aggregate_runtime_evidence_scope"],
            "counts_statuses_capability_keys_resource_trends_only",
        )
        self.assertGreater(len(validated["evidence"][-1]), 600)

    def test_rejects_aggregate_evidence_without_separate_consent(self):
        data = valid_request()
        data["evidence"].append(AGGREGATE_MARKER + json.dumps(valid_aggregate_snapshot()))
        with self.assertRaisesRegex(RequestValidationError, "without separate owner consent"):
            validate_request(data)

    def test_requires_payload_when_aggregate_consent_is_enabled(self):
        data = valid_request()
        data["privacy_review"]["aggregate_runtime_evidence_enabled"] = True
        data["privacy_review"]["aggregate_runtime_evidence_scope"] = "counts_statuses_capability_keys_resource_trends_only"
        with self.assertRaisesRegex(RequestValidationError, "requires exactly one"):
            validate_request(data)

    def test_rejects_unknown_nested_aggregate_field(self):
        data = valid_request()
        snapshot = valid_aggregate_snapshot()
        snapshot["remembered_facts"] = ["private"]
        data["evidence"].append(AGGREGATE_MARKER + json.dumps(snapshot))
        data["privacy_review"]["aggregate_runtime_evidence_enabled"] = True
        data["privacy_review"]["aggregate_runtime_evidence_scope"] = "counts_statuses_capability_keys_resource_trends_only"
        with self.assertRaisesRegex(RequestValidationError, "unsupported or missing fields"):
            validate_request(data)

    def test_rejects_free_text_in_aggregate_capability_keys(self):
        data = valid_request()
        snapshot = valid_aggregate_snapshot()
        snapshot["available_capability_keys"] = ["the owner told Friday a private fact"]
        data["evidence"].append(AGGREGATE_MARKER + json.dumps(snapshot))
        data["privacy_review"]["aggregate_runtime_evidence_enabled"] = True
        data["privacy_review"]["aggregate_runtime_evidence_scope"] = "counts_statuses_capability_keys_resource_trends_only"
        with self.assertRaisesRegex(RequestValidationError, "not free text"):
            validate_request(data)

    def test_rejects_malformed_aggregate_json(self):
        data = valid_request()
        data["evidence"].append(AGGREGATE_MARKER + "{not-json")
        data["privacy_review"]["aggregate_runtime_evidence_enabled"] = True
        data["privacy_review"]["aggregate_runtime_evidence_scope"] = "counts_statuses_capability_keys_resource_trends_only"
        with self.assertRaisesRegex(RequestValidationError, "not valid JSON"):
            validate_request(data)

    def test_rejects_mismatched_aggregate_evidence_consent(self):
        data = valid_request()
        data["privacy_review"]["aggregate_runtime_evidence_enabled"] = True
        data["privacy_review"]["aggregate_runtime_evidence_scope"] = "none"
        with self.assertRaisesRegex(RequestValidationError, "do not match"):
            validate_request(data)

    def test_rejects_non_string_aggregate_scope(self):
        data = valid_request()
        data["privacy_review"]["aggregate_runtime_evidence_scope"] = []
        with self.assertRaisesRegex(RequestValidationError, "scope is unsupported"):
            validate_request(data)

    def test_rejects_unsupported_privacy_metadata(self):
        data = valid_request()
        data["privacy_review"]["raw_memory_included"] = True
        with self.assertRaisesRegex(RequestValidationError, "unsupported fields"):
            validate_request(data)

    def test_rejects_oversized_evidence_item(self):
        data = valid_request()
        data["evidence"] = ["x" * 8001]
        with self.assertRaisesRegex(RequestValidationError, "exceeds 8000"):
            validate_request(data)

    def test_rejects_id_mismatch(self):
        with self.assertRaisesRegex(RequestValidationError, "does not match"):
            validate_request(valid_request(), expected_id="different")

    def test_rejects_unsafe_id(self):
        data = valid_request()
        data["request_id"] = "../main"
        with self.assertRaisesRegex(RequestValidationError, "unsupported characters"):
            validate_request(data)


if __name__ == "__main__":
    unittest.main()
