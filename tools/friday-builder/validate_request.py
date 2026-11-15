#!/usr/bin/env python3
"""Validate a privacy-reviewed Friday repository-development request.

This validator uses only the Python standard library. It intentionally accepts
a narrow envelope: no chat transcript, attachments, credentials or arbitrary
payload fields can be passed to a repository worker.
"""
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
    """Raised when an envelope is missing, unsafe or malformed."""


def _text(value: Any, field: str, maximum: int, *, required: bool = True) -> str:
    if not isinstance(value, str):
        raise RequestValidationError(f"{field} must be a string")
    value = value.strip()
    if required and not value:
        raise RequestValidationError(f"{field} must not be empty")
    if len(value) > maximum:
        raise RequestValidationError(f"{field} exceeds {maximum} characters")
    return value


def _string_list(value: Any, field: str, maximum_items: int, maximum_text: int,
                 *, minimum_items: int = 0) -> list[str]:
    if not isinstance(value, list):
        raise RequestValidationError(f"{field} must be an array")
    if len(value) < minimum_items or len(value) > maximum_items:
        raise RequestValidationError(
            f"{field} must contain between {minimum_items} and {maximum_items} items"
        )
    result: list[str] = []
    for index, item in enumerate(value):
        result.append(_text(item, f"{field}[{index}]", maximum_text))
    return result


def validate_request(data: Any, expected_id: str | None = None) -> dict[str, Any]:
    if not isinstance(data, dict):
        raise RequestValidationError("request root must be a JSON object")
    unknown = set(data) - REQUIRED_KEYS
    missing = REQUIRED_KEYS - set(data)
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

    created_at = _text(data["created_at"], "created_at", 40)
    try:
        parsed_date = datetime.fromisoformat(created_at.replace("Z", "+00:00"))
    except ValueError as exc:
        raise RequestValidationError("created_at must be ISO-8601") from exc
    if parsed_date.tzinfo is None:
        raise RequestValidationError("created_at must include a timezone")

    source_revision = _text(data["source_revision"], "source_revision", 128)
    intended_outcome = _text(data["intended_outcome"], "intended_outcome", 1200)
    evidence = _string_list(data["evidence"], "evidence", 12, 600, minimum_items=1)
    acceptance = _string_list(
        data["acceptance_criteria"], "acceptance_criteria", 12, 600, minimum_items=1
    )
    risks = _string_list(data["risks"], "risks", 8, 500)
    rollback = _text(data["rollback_expectation"], "rollback_expectation", 800)
    questions = _string_list(data["unresolved_questions"], "unresolved_questions", 8, 500)

    review = data["privacy_review"]
    if not isinstance(review, dict):
        raise RequestValidationError("privacy_review must be an object")
    required_review = {"status", "reviewed_by", "reviewed_at"}
    if set(review) != required_review:
        raise RequestValidationError("privacy_review must contain only status, reviewed_by, reviewed_at")
    if review["status"] != "approved":
        raise RequestValidationError("privacy review must be approved before worker execution")
    _text(review["reviewed_by"], "privacy_review.reviewed_by", 120)
    reviewed_at = _text(review["reviewed_at"], "privacy_review.reviewed_at", 40)
    try:
        reviewed_date = datetime.fromisoformat(reviewed_at.replace("Z", "+00:00"))
    except ValueError as exc:
        raise RequestValidationError("privacy_review.reviewed_at must be ISO-8601") from exc
    if reviewed_date.tzinfo is None:
        raise RequestValidationError("privacy_review.reviewed_at must include a timezone")

    # Scan the complete serialized envelope so a secret cannot hide in evidence
    # or a free-text acceptance criterion.
    serialized = json.dumps(data, ensure_ascii=True)
    for pattern in SECRET_PATTERNS:
        if pattern.search(serialized):
            raise RequestValidationError("request appears to contain a credential or private key")

    return {
        "schema_version": 1,
        "request_id": request_id,
        "created_at": created_at,
        "source_revision": source_revision,
        "intended_outcome": intended_outcome,
        "evidence": evidence,
        "acceptance_criteria": acceptance,
        "risks": risks,
        "rollback_expectation": rollback,
        "unresolved_questions": questions,
        "privacy_review": review,
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("request_file", type=Path)
    parser.add_argument("--expected-id")
    args = parser.parse_args()

    try:
        if args.request_file.stat().st_size > MAX_BYTES:
            raise RequestValidationError(f"request exceeds {MAX_BYTES} bytes")
        raw = args.request_file.read_text(encoding="utf-8")
        request = validate_request(json.loads(raw), args.expected_id)
    except (OSError, UnicodeError, json.JSONDecodeError, RequestValidationError) as exc:
        print(f"INVALID Friday development request: {exc}", file=sys.stderr)
        return 2

    print(
        "VALID request "
        + request["request_id"]
        + " | source="
        + request["source_revision"]
        + " | acceptance_criteria="
        + str(len(request["acceptance_criteria"]))
        + " | privacy_review="
        + request["privacy_review"]["status"]
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
