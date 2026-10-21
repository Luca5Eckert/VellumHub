#!/usr/bin/env python3
"""AFF-BOUND-003: reject deployable-to-deployable Maven dependencies.

Runs without third-party libraries and deliberately scans all POM dependency
declarations (including profiles and dependencyManagement). Test-only E2E is
excluded from the producer set; the five deployable services are not.
"""
from __future__ import annotations

import sys
import xml.etree.ElementTree as ET
from pathlib import Path

NS = {"m": "http://maven.apache.org/POM/4.0.0"}
SERVICES = ("catalog", "engagement", "user", "recommendation", "gateway")


def dependencies(pom: Path) -> set[tuple[str, str]]:
    root = ET.parse(pom).getroot()
    result = set()
    # Include dependency declarations in build profiles and dependencyManagement.
    for node in root.findall(".//m:dependency", NS):
        group = node.findtext("m:groupId", default="", namespaces=NS).strip()
        artifact = node.findtext("m:artifactId", default="", namespaces=NS).strip()
        if group and artifact:
            result.add((group, artifact))
    return result


def check(root: Path) -> list[str]:
    errors = []
    expected = {f"{name}-service" for name in SERVICES}
    reactor_pom = root / "pom.xml"
    if not reactor_pom.is_file():
        return ["AFF-BOUND-003: missing root reactor pom.xml"]
    reactor = ET.parse(reactor_pom).getroot()
    module_paths = {e.text.strip() for e in reactor.findall("./m:modules/m:module", NS) if e.text}
    for artifact in sorted(expected):
        module = f"services/{artifact}"
        pom = root / module / "pom.xml"
        if module not in module_paths or not pom.is_file():
            errors.append(f"AFF-BOUND-003: missing deployable Maven module {module}")
            continue
        own = dependencies(pom)
        for group, target in sorted(own):
            if target in expected and target != artifact:
                errors.append(
                    f"AFF-BOUND-003: {artifact} declares forbidden dependency "
                    f"{group}:{target} in {pom.relative_to(root)}"
                )
    return errors


def main() -> int:
    root = Path(__file__).resolve().parents[2]
    findings = check(root)
    if findings:
        print("\n".join(findings), file=sys.stderr)
        return 1
    print("AFF-BOUND-003: PASS — no service-to-service Maven declarations")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
