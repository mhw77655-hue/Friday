#!/usr/bin/env python3
"""Validate a bounded Friday repository-development request."""
from __future__ import annotations

import argparse
import json
import re
import sys
from datetime import datetime
from pathlib import Path
from typing import Any

MAX_BYTES = 32 * 1024
REQUEST_ID = re.compile(r"^[a-z0-9][a-z0-9._-]{0,63}$")
SECRET_PATTERNS = (
    re.compile(r"gh[pousr]_[A-Za-z0-9]{20,}"),
    re.compile(r"github_pat_[A-Za-z0-9_]{20,}"),
    re.compile(r"AKIA[0-9A-Z]{16}"),
    re.compile(r"sk-[A-Za-z0-9]{20,}"),
    re.compile(r"-----BEGIN [A-Z ]*PRIVATE KEY-----"),
)
REQUIRED_KEYS = {
    "schema_version", "request_id", "created_at", "source_revision",
    "intended_outcome", "evidence", "acceptance_criteria", "risks",
    "rollback_expectation", "unresolved_questions", "privacy_review",
}


class RequestValidationError(ValueError):
    pass


def _text(value: Any, field: str, maximum: int) -> str:
    if not isinstance(value, str):
        raise RequestValidationError(f"{field} must be a string")
    value = value.strip()
    if not value:
        raise RequestValidationError(f"{field} must not be empty")
    if len(value) > maximum:
        raise RequestValidationError(f"{field} exceeds {maximum} characters")
    return value


def _list(value: Any, field: str, max_items: int, max_text: int, minimum: int = 0) -> list[str]:
    if not isinstance(value, list) or not minimum <= len(value) <= max_items:
        raise RequestValidationError(f"{field} must contain between {minimum} and {max_items} items")
    return [_text(item, f"{field}[{i}]", max_text) for i, item in enumerate(value)]


def _aware_timestamp(value: Any, field: str) -> str:
    result = _text(value, field, 40)
    try:
        parsed = datetime.fromisoformat(result.replace("Z", "+00:00"))
    except ValueError as exc:
        raise RequestValidationError(f"{field} must be ISO-8601") from exc
    if parsed.tzinfo is None:
        raise RequestValidationError(f"{field} must include a timezone")
    return result


def validate_request(data: Any, expected_id: str | None = None) -> dict[str, Any]:
    if not isinstance(data, dict):
        raise RequestValidationError("request root must be an object")
    unknown, missing = set(data) - REQUIRED_KEYS, REQUIRED_KEYS - set(data)
    if unknown:
        raise RequestValidationError("unsupported fields: " + ", ".join(sorted(unknown)))
    if missing:
        raise RequestValidationError("missing fields: " + ", ".join(sorted(missing)))
    if data["schema_version"] != 1 or isinstance(data["schema_version"], bool):
        raise RequestValidationError("schema_version must be integer 1")

    request_id = _text(data["request_id"], "request_id", 64)
    if not REQUEST_ID.fullmatch(request_id):
        raise RequestValidationError("request_id contains unsupported characters")
    if expected_id is not None and request_id != expected_id:
        raise RequestValidationError("request_id does not match the workflow input")

    created_at = _aware_timestamp(data["created_at"], "created_at")
    source_revision = _text(data["source_revision"], "source_revision", 128)
    intended_outcome = _text(data["intended_outcome"], "intended_outcome", 4000)
    evidence = _list(data["evidence"], "evidence", 12, 8000, 1)
    acceptance = _list(data["acceptance_criteria"], "acceptance_criteria", 12, 600, 1)
    risks = _list(data["risks"], "risks", 8, 500)
    rollback = _text(data["rollback_expectation"], "rollback_expectation", 800)
    questions = _list(data["unresolved_questions"], "unresolved_questions", 8, 500)

    review = data["privacy_review"]
    required_review_keys = {"status", "reviewed_by", "reviewed_at"}
    optional_review_keys = {"aggregate_runtime_evidence_enabled", "aggregate_runtime_evidence_scope"}
    if (
        not isinstance(review, dict)
        or not required_review_keys.issubset(review)
        or set(review) - required_review_keys - optional_review_keys
    ):
        raise RequestValidationError("privacy_review contains missing or unsupported fields")
    if review["status"] != "approved":
        raise RequestValidationError("privacy review must be approved before worker execution")
    reviewed_by = _text(review["reviewed_by"], "privacy_review.reviewed_by", 120)
    reviewed_at = _aware_timestamp(review["reviewed_at"], "privacy_review.reviewed_at")
    aggregate_enabled = review.get("aggregate_runtime_evidence_enabled", False)
    aggregate_scope = review.get("aggregate_runtime_evidence_scope", "none")
    if not isinstance(aggregate_enabled, bool):
        raise RequestValidationError("privacy_review.aggregate_runtime_evidence_enabled must be boolean")
    allowed_scopes = {"none", "counts_statuses_capability_keys_resource_trends_only"}
    if not isinstance(aggregate_scope, str) or aggregate_scope not in allowed_scopes:
        raise RequestValidationError("privacy_review.aggregate_runtime_evidence_scope is unsupported")
    if aggregate_enabled != (aggregate_scope != "none"):
        raise RequestValidationError("aggregate evidence consent and scope do not match")

    serialized = json.dumps(data, ensure_ascii=True)
    if any(pattern.search(serialized) for pattern in SECRET_PATTERNS):
        raise RequestValidationError("request appears to contain a credential or private key")

    return {
        "schema_version": 1, "request_id": request_id, "created_at": created_at,
        "source_revision": source_revision, "intended_outcome": intended_outcome,
        "evidence": evidence, "acceptance_criteria": acceptance, "risks": risks,
        "rollback_expectation": rollback, "unresolved_questions": questions,
        "privacy_review": {
            "status": "approved", "reviewed_by": reviewed_by, "reviewed_at": reviewed_at,
            "aggregate_runtime_evidence_enabled": aggregate_enabled,
            "aggregate_runtime_evidence_scope": aggregate_scope,
        },
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("request_file", type=Path)
    parser.add_argument("--expected-id")
    args = parser.parse_args()
    try:
        if args.request_file.stat().st_size > MAX_BYTES:
            raise RequestValidationError(f"request exceeds {MAX_BYTES} bytes")
        request = validate_request(json.loads(args.request_file.read_text(encoding="utf-8")), args.expected_id)
    except (OSError, UnicodeError, json.JSONDecodeError, RequestValidationError) as exc:
        print(f"INVALID Friday development request: {exc}", file=sys.stderr)
        return 2
    print(f"VALID request {request['request_id']} | source={request['source_revision']} | privacy_review=approved")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
