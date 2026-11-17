from __future__ import annotations
import unittest
from validate_request import RequestValidationError, validate_request


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
        data = valid_request()
        data["evidence"].append("FRIDAY_AGGREGATE_RUNTIME_EVIDENCE_JSON: " + ("{\"status\":\"PARTIAL\"}" * 500))
        data["privacy_review"]["aggregate_runtime_evidence_enabled"] = True
        data["privacy_review"]["aggregate_runtime_evidence_scope"] = "counts_statuses_capability_keys_resource_trends_only"

        validated = validate_request(data)

        self.assertTrue(validated["privacy_review"]["aggregate_runtime_evidence_enabled"])
        self.assertEqual(
            validated["privacy_review"]["aggregate_runtime_evidence_scope"],
            "counts_statuses_capability_keys_resource_trends_only",
        )
        self.assertGreater(len(validated["evidence"][-1]), 600)

    def test_rejects_mismatched_aggregate_evidence_consent(self):
        data = valid_request()
        data["privacy_review"]["aggregate_runtime_evidence_enabled"] = True
        data["privacy_review"]["aggregate_runtime_evidence_scope"] = "none"
        with self.assertRaisesRegex(RequestValidationError, "do not match"):
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
