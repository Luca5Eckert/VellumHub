#!/usr/bin/env python3
"""Immutable SHA-bound Docker image archive verification for build-once releases.

The image config digest (.Id) is checked before scanning and after transfer.
The registry manifest digest is recorded separately after publication: an
image config digest is NOT interchangeable with an OCI registry digest.
"""
import argparse
import hashlib
import json
import pathlib
import re
import subprocess
import sys

SHA = re.compile(r"^[0-9a-f]{40}$")
IMAGE = re.compile(r"^ghcr\.io/luca5eckert/vellumhub-[a-z-]+:[0-9a-f]{40}$")
CONFIG = re.compile(r"^sha256:[0-9a-f]{64}$")


def sha256(path):
    digest = hashlib.sha256()
    with open(path, "rb") as source:
        for block in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def image_id(tag):
    result = subprocess.run(["docker", "image", "inspect", "--format", "{{.Id}}", tag],
                            capture_output=True, text=True, check=True)
    actual = result.stdout.strip()
    if not CONFIG.fullmatch(actual):
        raise ValueError(f"Unexpected image config id: {actual}")
    return actual


def inspect_metadata(path, expected_sha, expected_tag):
    if not SHA.fullmatch(expected_sha) or not IMAGE.fullmatch(expected_tag):
        raise ValueError("Expected a full commit SHA and an allowed VellumHub image tag")
    info = json.loads(pathlib.Path(path).read_text(encoding="utf8"))
    if info.get("schema_version") != 1:
        raise ValueError("Unsupported image artifact metadata schema")
    if info.get("source_sha") != expected_sha or info.get("image_tag") != expected_tag:
        raise ValueError("Artifact source SHA or image identity mismatch")
    if not CONFIG.fullmatch(info.get("config_digest", "")):
        raise ValueError("Invalid image config digest")
    if not re.fullmatch(r"[0-9a-f]{64}", info.get("archive_sha256", "")):
        raise ValueError("Invalid archive hash")
    return info


def main():
    cli = argparse.ArgumentParser()
    cli.add_argument("mode", choices=("record", "verify", "loaded"))
    cli.add_argument("--sha", required=True)
    cli.add_argument("--image", required=True)
    cli.add_argument("--archive", required=True, type=pathlib.Path)
    cli.add_argument("--metadata", required=True, type=pathlib.Path)
    args = cli.parse_args()
    if not args.archive.is_file() or not args.archive.stat().st_size:
        raise ValueError("Missing or empty image archive")
    if not SHA.fullmatch(args.sha) or not IMAGE.fullmatch(args.image):
        raise ValueError("Untrusted or unpinned image identifier")
    if args.mode == "record":
        args.metadata.parent.mkdir(parents=True, exist_ok=True)
        data = {"schema_version": 1, "source_sha": args.sha,
                "image_tag": args.image, "config_digest": image_id(args.image),
                "archive_sha256": sha256(args.archive),
                "archive_size_bytes": args.archive.stat().st_size}
        args.metadata.write_text(json.dumps(data, indent=2) + "\n", encoding="utf8")
    else:
        data = inspect_metadata(args.metadata, args.sha, args.image)
        if data["archive_sha256"] != sha256(args.archive):
            raise ValueError("Transferred image archive hash mismatch")
        if data["archive_size_bytes"] != args.archive.stat().st_size:
            raise ValueError("Transferred image archive size mismatch")
        if args.mode == "loaded" and data["config_digest"] != image_id(args.image):
            raise ValueError("Docker image config changed between scan and publish")
    print(f"image artifact {args.mode}: PASS ({args.image})")


if __name__ == "__main__":
    try:
        main()
    except (ValueError, subprocess.CalledProcessError, OSError, json.JSONDecodeError) as err:
        print(f"image artifact validation failed: {err}", file=sys.stderr)
        sys.exit(1)
