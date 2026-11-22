#!/usr/bin/env bash
set -euo pipefail

# Retry only transient repository/network failures (not Kotlin or test failures).
# Maven Central occasionally rate-limits parallel GitHub-hosted runners.
attempt=1
max_attempts=4
while true; do
  log="$(mktemp)"
  set +e
  ./gradlew --no-configuration-cache "$@" 2>&1 | tee "$log"
  status=${PIPESTATUS[0]}
  set -e
  if [[ "$status" -eq 0 ]]; then
    rm -f "$log"
    exit 0
  fi
  if [[ "$attempt" -ge "$max_attempts" ]] ||
     ! grep -Eqi '429|Too Many Requests|Could not GET|Read timed out|Connection reset|temporarily unavailable' "$log"; then
    rm -f "$log"
    exit "$status"
  fi
  rm -f "$log"
  delay=$((attempt * 15))
  echo "::warning::Transient dependency/network failure. Retrying Gradle in ${delay}s (attempt $((attempt + 1))/$max_attempts)."
  sleep "$delay"
  attempt=$((attempt + 1))
done
