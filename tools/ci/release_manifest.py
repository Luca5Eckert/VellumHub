#!/usr/bin/env python3
"""Fail-closed immutable six-service release manifest.

Inputs must come from the same SHA-bound GitHub Actions workflow run.
A manifest records a *set* of six published digests; it does not pretend
that GHCR offers atomic multi-image publication or production promotion.
"""
import argparse
import json
import pathlib
import re

SERVICES = ("auth", "gateway", "user", "catalog", "engagement", "recommendation")
SHA = re.compile(r"^[0-9a-f]{40}$")
DIGEST = re.compile(r"^sha256:[0-9a-f]{64}$")


def assemble(root, sha):
    if not SHA.fullmatch(sha):
        raise ValueError("Full source SHA is required")
    result = {}
    for service in SERVICES:
        image = f"ghcr.io/luca5eckert/vellumhub-{service}"
        folder = pathlib.Path(root) / f"release-image-reference-vellumhub-{service}"
        reference = folder / "image-reference.txt"
        metadata = folder / f"vellumhub-{service}.json"
        sbom = folder / f"vellumhub-{service}.cdx.json"
        if not all(path.is_file() and path.stat().st_size for path in (reference, metadata, sbom)):
            raise ValueError(f"Incomplete release artifact for {service}")
        ref = reference.read_text(encoding="utf-8").strip()
        prefix = image + "@"
        if not ref.startswith(prefix) or not DIGEST.fullmatch(ref[len(prefix):]):
            raise ValueError(f"Invalid or mismatched registry digest for {service}")
        meta = json.loads(metadata.read_text(encoding="utf-8"))
        if meta.get("source_sha") != sha or meta.get("image_tag") != f"{image}:{sha}":
            raise ValueError(f"Wrong SHA or source tag in {service} release artifact")
        if not re.fullmatch(r"sha256:[0-9a-f]{64}", meta.get("config_digest", "")):
            raise ValueError(f"Invalid Docker config digest for {service}")
        bill = json.loads(sbom.read_text(encoding="utf-8"))
        if bill.get("bomFormat") != "CycloneDX":
            raise ValueError(f"Missing CycloneDX in {service} release artifact")
        result[service] = {"image": image, "reference": ref,
                           "registry_manifest_digest": ref[len(prefix):],
                           "scanned_config_digest": meta["config_digest"],
                           "sbom_format": "CycloneDX"}
    if len(result) != len(SERVICES):
        raise ValueError("Release must include all six independent services")
    return {"schema_version": 1, "source_sha": sha, "status": "COMPLETE",
            "scope": "six GHCR images published; not deployed",
            "services": result}


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--artifacts", type=pathlib.Path, required=True)
    p.add_argument("--sha", required=True)
    p.add_argument("--output", type=pathlib.Path, required=True)
    args = p.parse_args()
    data = assemble(args.artifacts, args.sha)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")
    print(f"Complete SHA-pinned release set: {len(data['services'])}/6")


if __name__ == "__main__":
    main()
