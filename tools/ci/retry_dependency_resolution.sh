#!/usr/bin/env bash
# Retry transient Maven dependency-resolution failures without hiding test failures.
set -uo pipefail

if (( $# == 0 )); then
  echo 'Usage: retry_dependency_resolution.sh COMMAND [ARG ...]' >&2
  exit 2
fi

max_attempts=3
retry_delay_seconds=${RETRY_DEPENDENCY_DELAY_SECONDS:-15}
if [[ ! "$retry_delay_seconds" =~ ^[0-9]+$ ]]; then
  echo 'RETRY_DEPENDENCY_DELAY_SECONDS must be a non-negative integer' >&2
  exit 2
fi

for (( attempt=1; attempt<=max_attempts; attempt++ )); do
  command=("$@")
  # Maven caches failed resolution attempts in *.lastUpdated. Force a new lookup
  # after a transient repository failure, rather than replaying the cached miss.
  if (( attempt > 1 )) && [[ "${command[0]##*/}" == mvn || "${command[0]##*/}" == mvnw ]]; then
    command=("${command[0]}" -U "${command[@]:1}")
  fi

  log_file=$(mktemp)
  "${command[@]}" 2>&1 | tee "$log_file"
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
  delay=$((retry_delay_seconds * attempt))
  echo "Maven dependency resolution failed (attempt $attempt/$max_attempts); retrying in ${delay}s with refreshed metadata." >&2
  sleep "$delay"
done
