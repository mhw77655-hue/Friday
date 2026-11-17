#!/usr/bin/env python3
"""Validate a bounded Friday repository-development request."""
from __future__ import annotations

import argparse
import json
import math
import re
import sys
from datetime import datetime
from pathlib import Path
from typing import Any

MAX_BYTES = 32 * 1024
MAX_AGGREGATE_BYTES = 8 * 1024
REQUEST_ID = re.compile(r"^[a-z0-9][a-z0-9._-]{0,63}$")
SAFE_IDENTIFIER = re.compile(r"^[A-Za-z0-9_.$:/-]{1,160}$")
SAFE_STATUS = re.compile(r"^[A-Z_]{1,48}$")
AGGREGATE_MARKER = "FRIDAY_AGGREGATE_RUNTIME_EVIDENCE_JSON (content-free; no personal content): "
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

AGGREGATE_REQUIRED = {
    "schema_version", "observed_at_ms", "privacy_scope",
    "runtime_readiness", "identity_record_present",
}
AGGREGATE_ALLOWED = AGGREGATE_REQUIRED | {
    "experience_count", "association_count", "plan_status",
    "plan_step_status_counts", "capability_stage",
    "available_capability_keys", "missing_capability_keys",
    "symbiotic_mode", "symbiotic_context_token_budget",
    "symbiotic_max_concurrent_expensive_tasks", "symbiotic_memory_reserve_mb",
    "self_study",
}
SELF_STUDY_FIELDS = {
    "source_revision", "source_file_count", "source_symbol_count",
    "source_package_count", "source_dependency_edge_count", "graph_node_count",
    "graph_edge_count", "reachable_node_count", "unreachable_node_count",
    "missing_invariant_count", "declared_missing_capability_keys",
    "known_limitation_keys", "host_samples", "minimum_available_memory_mb",
    "average_cpu_load_ema_percent", "thermal_pressure_samples",
    "low_memory_samples", "additional_memory_reserve_mb",
    "maximum_observed_process_pss_mb", "minimum_battery_percent",
    "liquid_generation", "liquid_prediction_error", "organ_strategy_keys",
    "organ_failure_counts", "next_focus_count",
}
SELF_STUDY_INTEGER_FIELDS = SELF_STUDY_FIELDS - {
    "source_revision", "declared_missing_capability_keys", "known_limitation_keys",
    "average_cpu_load_ema_percent", "organ_strategy_keys",
    "organ_failure_counts", "liquid_prediction_error",
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


def _integer(value: Any, field: str, maximum: int = 2**63 - 1) -> None:
    if type(value) is not int or value < 0 or value > maximum:
        raise RequestValidationError(f"{field} must be a non-negative bounded integer")


def _number(value: Any, field: str, maximum: float = 1e12) -> None:
    if isinstance(value, bool) or not isinstance(value, (int, float)):
        raise RequestValidationError(f"{field} must be a number")
    if not math.isfinite(value) or value < 0 or value > maximum:
        raise RequestValidationError(f"{field} must be finite and within bounds")


def _finite_number(value: Any, field: str, minimum: float = -1e12, maximum: float = 1e12) -> None:
    if isinstance(value, bool) or not isinstance(value, (int, float)):
        raise RequestValidationError(f"{field} must be a number")
    if not math.isfinite(value) or value < minimum or value > maximum:
        raise RequestValidationError(f"{field} must be finite and within bounds")


def _identifier(value: Any, field: str, maximum: int = 160) -> None:
    if not isinstance(value, str) or len(value) > maximum or not SAFE_IDENTIFIER.fullmatch(value):
        raise RequestValidationError(f"{field} must be a bounded identifier, not free text")


def _identifier_list(value: Any, field: str, maximum_items: int) -> None:
    if not isinstance(value, list) or len(value) > maximum_items:
        raise RequestValidationError(f"{field} must be a bounded identifier list")
    for index, item in enumerate(value):
        _identifier(item, f"{field}[{index}]")


def _count_map(value: Any, field: str, maximum_items: int, status_keys: bool = False) -> None:
    if not isinstance(value, dict) or len(value) > maximum_items:
        raise RequestValidationError(f"{field} must be a bounded count map")
    for key, count in value.items():
        pattern = SAFE_STATUS if status_keys else SAFE_IDENTIFIER
        if not isinstance(key, str) or not pattern.fullmatch(key):
            raise RequestValidationError(f"{field} contains an unsafe key")
        _integer(count, f"{field}.{key}")


def _validate_self_study(value: Any) -> None:
    if not isinstance(value, dict):
        raise RequestValidationError("aggregate self_study must be an object or null")
    unknown = set(value) - SELF_STUDY_FIELDS
    missing = SELF_STUDY_FIELDS - set(value)
    if unknown or missing:
        raise RequestValidationError("aggregate self_study contains unsupported or missing fields")

    _identifier(value["source_revision"], "self_study.source_revision", 80)
    for field in SELF_STUDY_INTEGER_FIELDS:
        _integer(value[field], f"self_study.{field}")
    _number(value["average_cpu_load_ema_percent"], "self_study.average_cpu_load_ema_percent", 100)
    _finite_number(value["liquid_prediction_error"], "self_study.liquid_prediction_error")
    if value["minimum_battery_percent"] > 100:
        raise RequestValidationError("self_study.minimum_battery_percent must be between 0 and 100")
    _identifier_list(value["declared_missing_capability_keys"], "self_study.declared_missing_capability_keys", 20)
    _identifier_list(value["known_limitation_keys"], "self_study.known_limitation_keys", 20)
    _identifier_list(value["organ_strategy_keys"], "self_study.organ_strategy_keys", 24)
    _count_map(value["organ_failure_counts"], "self_study.organ_failure_counts", 24)


def _validate_aggregate_payload(evidence: list[str], consent: bool) -> None:
    matches = [item for item in evidence if AGGREGATE_MARKER in item]
    if not consent:
        if matches:
            raise RequestValidationError("aggregate runtime evidence was included without separate owner consent")
        return
    if len(matches) != 1 or not matches[0].startswith(AGGREGATE_MARKER):
        raise RequestValidationError("aggregate evidence consent requires exactly one marked runtime snapshot")

    raw = matches[0][len(AGGREGATE_MARKER):].strip()
    if not raw or len(raw.encode("utf-8")) > MAX_AGGREGATE_BYTES:
        raise RequestValidationError("aggregate runtime evidence exceeds the 8 KiB limit")
    try:
        payload = json.loads(raw)
    except json.JSONDecodeError as exc:
        raise RequestValidationError("aggregate runtime evidence is not valid JSON") from exc
    if not isinstance(payload, dict):
        raise RequestValidationError("aggregate runtime evidence must be a JSON object")

    unknown = set(payload) - AGGREGATE_ALLOWED
    missing = AGGREGATE_REQUIRED - set(payload)
    if unknown or missing:
        raise RequestValidationError("aggregate runtime evidence contains unsupported or missing fields")
    if type(payload["schema_version"]) is not int or payload["schema_version"] != 1:
        raise RequestValidationError("aggregate runtime evidence schema_version must be integer 1")
    _integer(payload["observed_at_ms"], "aggregate.observed_at_ms")
    if payload["privacy_scope"] != "aggregate_only_no_content":
        raise RequestValidationError("aggregate runtime evidence privacy scope is unsupported")
    _identifier(payload["runtime_readiness"], "aggregate.runtime_readiness", 48)
    if not isinstance(payload["identity_record_present"], bool):
        raise RequestValidationError("aggregate.identity_record_present must be boolean")

    for field in ("experience_count", "association_count", "capability_stage",
                  "symbiotic_context_token_budget", "symbiotic_max_concurrent_expensive_tasks",
                  "symbiotic_memory_reserve_mb"):
        if field in payload:
            _integer(payload[field], f"aggregate.{field}")
    for field in ("plan_status", "symbiotic_mode"):
        if field in payload:
            _identifier(payload[field], f"aggregate.{field}", 48)
    if "plan_step_status_counts" in payload:
        _count_map(payload["plan_step_status_counts"], "aggregate.plan_step_status_counts", 32, status_keys=True)
    for field in ("available_capability_keys", "missing_capability_keys"):
        if field in payload:
            _identifier_list(payload[field], f"aggregate.{field}", 32)
    if "self_study" in payload and payload["self_study"] is not None:
        _validate_self_study(payload["self_study"])


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
    _validate_aggregate_payload(evidence, aggregate_enabled)

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
