#!/usr/bin/env bash
# Retry only transient Maven dependency-resolution failures; never mask test failures.
set -uo pipefail

if (( $# == 0 )); then
  echo 'Usage: retry_dependency_resolution.sh COMMAND [ARG ...]' >&2
  exit 2
fi

max_attempts=3
for (( attempt=1; attempt<=max_attempts; attempt++ )); do
  log_file=$(mktemp)
  "$@" 2>&1 | tee "$log_file"
  command_status=${PIPESTATUS[0]}

  if (( command_status == 0 )); then
    rm -f "$log_file"
    exit 0
  fi

  if (( attempt == max_attempts )) || ! grep -Eiq \
    'Too Many Requests|status code: 429|Plugin org\.apache\.maven\.plugins:.*could not be resolved|Could not (find|transfer) artifact .* (central|repo\.maven\.apache\.org)' \
    "$log_file"; then
    rm -f "$log_file"
    exit "$command_status"
  fi

  rm -f "$log_file"
  delay=$((15 * attempt))
  echo "Maven dependency resolution failed (attempt $attempt/$max_attempts); retrying in ${delay}s." >&2
  sleep "$delay"
done
