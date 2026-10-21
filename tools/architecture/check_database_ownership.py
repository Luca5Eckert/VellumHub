#!/usr/bin/env python3
"""AFF-DATA-004 (configuration): assert local Compose DB ownership wiring.

Static configuration evidence only. It does not prove database ACL separation.
"""
from pathlib import Path
import re
import sys

OWNERS = {
    "catalog": ("postgres-catalog", "catalog_db"),
    "engagement": ("postgres-engagement", "engagement_db"),
    "user": ("postgres-user", "user_db"),
    "recommendation": ("postgres-recommendation", "recommendation_db"),
}


def service_sections(document: str) -> dict[str, str]:
    match = re.search(r"(?m)^services:\s*$", document)
    if not match:
        return {}
    section = document[match.end():]
    # Stop at the next top-level YAML key (volumes, networks, etc.).
    end = re.search(r"(?m)^[a-z][\w-]*:\s*$", section)
    if end:
        section = section[:end.start()]
    starts = list(re.finditer(r"(?m)^  ([a-z0-9-]+):\s*$", section))
    return {
        m.group(1): section[m.end():(starts[i + 1].start() if i + 1 < len(starts) else len(section))]
        for i, m in enumerate(starts)
    }


def check(compose: str) -> list[str]:
    sections = service_sections(compose)
    errors = []
    endpoints = set()
    for owner, (host, database) in OWNERS.items():
        service = sections.get(f"{owner}-service")
        postgres = sections.get(host)
        if service is None or postgres is None:
            errors.append(f"AFF-DATA-004: missing {owner} application or {host}")
            continue
        expected = f"jdbc:postgresql://{host}:5432/{database}"
        pattern = r"(?m)^\s+SPRING_DATASOURCE_URL:\s*(\S+)\s*$"
        configured = re.findall(pattern, service)
        if configured != [expected]:
            errors.append(f"AFF-DATA-004: {owner}-service expected {expected}, got {configured}")
        if not re.search(rf"(?m)^\s+POSTGRES_DB:\s*{re.escape(database)}\s*$", postgres):
            errors.append(f"AFF-DATA-004: {host} does not own {database}")

        # Application and database must reference the same domain-specific
        # credentials. Fail closed on shared credential interpolation keys.
        key = owner.upper()
        for setting, expected_var in (
            ("SPRING_DATASOURCE_USERNAME", f"POSTGRES_{key}_USER"),
            ("SPRING_DATASOURCE_PASSWORD", f"POSTGRES_{key}_PASSWORD"),
        ):
            match = re.search(rf"(?m)^\\s+{setting}:\\s*\\$\\{{([A-Z_]+):\\?[^}}]+}}\\s*$", service)
            if match is None or match.group(1) != expected_var:
                errors.append(f"AFF-DATA-004: {owner}-service must use {expected_var} for {setting}")
        for setting, expected_var in (
            ("POSTGRES_USER", f"POSTGRES_{key}_USER"),
            ("POSTGRES_PASSWORD", f"POSTGRES_{key}_PASSWORD"),
        ):
            match = re.search(rf"(?m)^\\s+{setting}:\\s*\\$\\{{([A-Z_]+):\\?[^}}]+}}\\s*$", postgres)
            if match is None or match.group(1) != expected_var:
                errors.append(f"AFF-DATA-004: {host} must use {expected_var} for {setting}")
        endpoints.update(configured)
    if len(endpoints) != len(OWNERS):
        errors.append("AFF-DATA-004: service PostgreSQL endpoints are not unique")
    return errors


def main() -> int:
    file = Path(__file__).resolve().parents[2] / "docker-compose.yml"
    errors = check(file.read_text(encoding="utf-8")) if file.exists() else ["AFF-DATA-004: missing docker-compose.yml"]
    for error in errors:
        print(error, file=sys.stderr)
    if not errors:
        print("AFF-DATA-004 (configuration): PASS — service database URLs match dedicated instances")
    return 1 if errors else 0


if __name__ == "__main__":
    raise SystemExit(main())
