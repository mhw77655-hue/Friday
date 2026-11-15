from __future__ import annotations

import copy
import unittest

from validate_request import RequestValidationError, validate_request


def valid_request() -> dict:
    return {
        "schema_version": 1,
        "request_id": "host-budget-001",
        "created_at": "2026-11-16T12:00:00+02:00",
        "source_revision": "e0556fef2ec85612effc40e93844f353c7bc1cc3",
        "intended_outcome": "Make learned host memory reserve observable in model admission.",
        "evidence": [
            "A reasoning failure increases the bounded reserve.",
            "No exact-device memory benchmark is attached."
        ],
        "acceptance_criteria": [
            "The reserve is clamped to the documented safe range.",
            "Tests prove that host pressure pauses background work."
        ],
        "risks": ["An excessive reserve could deny useful model tiers."],
        "rollback_expectation": "Revert the isolated change without altering continuity data.",
        "unresolved_questions": ["What exact reserve produces the best quality/latency trade-off on the target phone?"],
        "privacy_review": {
            "status": "approved",
            "reviewed_by": "repository-owner",
            "reviewed_at": "2026-11-16T12:05:00+02:00"
        }
    }


class ValidateRequestTests(unittest.TestCase):
    def test_accepts_a_bounded_privacy_reviewed_envelope(self) -> None:
        result = validate_request(valid_request(), expected_id="host-budget-001")
        self.assertEqual(result["request_id"], "host-budget-001")
        self.assertEqual(len(result["acceptance_criteria"]), 2)

    def test_rejects_unreviewed_request(self) -> None:
        request = valid_request()
        request["privacy_review"]["status"] = "pending"
        with self.assertRaisesRegex(RequestValidationError, "must be approved"):
            validate_request(request)

    def test_rejects_transcript_or_unknown_payload_fields(self) -> None:
        request = valid_request()
        request["conversation_transcript"] = "private dialogue"
        with self.assertRaisesRegex(RequestValidationError, "unsupported fields"):
            validate_request(request)

    def test_rejects_credentials_in_any_text_field(self) -> None:
        request = valid_request()
        request["evidence"].append("Accidentally copied token ghp_" + "A" * 36)
        with self.assertRaisesRegex(RequestValidationError, "credential"):
            validate_request(request)

    def test_rejects_missing_acceptance_criteria(self) -> None:
        request = valid_request()
        request["acceptance_criteria"] = []
        with self.assertRaisesRegex(RequestValidationError, "between 1 and 12"):
            validate_request(request)

    def test_rejects_request_id_mismatch(self) -> None:
        with self.assertRaisesRegex(RequestValidationError, "does not match"):
            validate_request(valid_request(), expected_id="different-id")

    def test_rejects_unsupported_request_id_characters(self) -> None:
        request = valid_request()
        request["request_id"] = "../main"
        with self.assertRaisesRegex(RequestValidationError, "unsupported characters"):
            validate_request(request)

    def test_rejects_naive_timestamps(self) -> None:
        request = valid_request()
        request["created_at"] = "2026-11-16T12:00:00"
        with self.assertRaisesRegex(RequestValidationError, "include a timezone"):
            validate_request(request)


if __name__ == "__main__":
    unittest.main()
