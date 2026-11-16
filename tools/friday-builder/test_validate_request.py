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
