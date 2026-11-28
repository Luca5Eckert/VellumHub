#!/usr/bin/env python3
"""Render the rehearsed single-node overlay with real HTTPS hosts and callback."""
import argparse
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
from urllib.parse import urlsplit


def domain(value):
    labels = value.split(".")
    if (
        len(value) > 253
        or len(labels) < 2
        or not all(re.fullmatch(r"[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?", label) for label in labels)
        or labels[-1] in {"example", "invalid", "local", "localhost", "test"}
    ):
        raise argparse.ArgumentTypeError("supply a real lowercase DNS hostname")
    return value


def callback_url(value):
    try:
        parsed = urlsplit(value)
        domain(parsed.netloc)
    except (ValueError, argparse.ArgumentTypeError):
        raise argparse.ArgumentTypeError("supply an HTTPS callback on a real DNS hostname") from None
    if parsed.scheme != "https" or not re.fullmatch(r"/[A-Za-z0-9_./~-]+", parsed.path):
        raise argparse.ArgumentTypeError("supply an HTTPS callback with a path")
    if parsed.fragment or parsed.query or value != f"https://{parsed.netloc}{parsed.path}":
        raise argparse.ArgumentTypeError("the HTTPS callback must not contain a query or fragment")
    return value


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--auth-host", type=domain, required=True)
    parser.add_argument("--api-host", type=domain, required=True)
    parser.add_argument("--callback", type=callback_url, required=True)
    args = parser.parse_args()
    app_origin = f"https://{urlsplit(args.callback).netloc}"
    if len({args.auth_host, args.api_host, urlsplit(args.callback).netloc}) != 3:
        parser.error("Auth, API, and the callback must use distinct hosts")

    source = Path(__file__).resolve().parents[1]
    with tempfile.TemporaryDirectory() as directory:
        root = Path(directory)
        shutil.copytree(source / "base", root / "base")
        for name in ("local", "staging"):
            shutil.copytree(source / "overlays" / name, root / "overlays" / name)
        staging = root / "overlays/staging"
        substitutions = {
            "settings.yaml": {
                "https://auth.vellumhub.test:8443": f"https://{args.auth_host}",
                "https://app.vellumhub.test/oauth/callback": args.callback,
                "https://app.vellumhub.test": app_origin,
            },
            "auth-ingress.yaml": {
                "auth.vellumhub.test": args.auth_host,
                "vellumhub-staging-tls": "vellumhub-aws-tls",
            },
            "gateway-ingress.yaml": {
                "api.vellumhub.test": args.api_host,
                "vellumhub-staging-tls": "vellumhub-aws-tls",
            },
        }
        for filename, replacements in substitutions.items():
            path = staging / filename
            content = path.read_text()
            for old, new in replacements.items():
                if old not in content:
                    parser.error(f"the staging template no longer contains {old} in {filename}")
                content = content.replace(old, new)
            path.write_text(content)
        result = subprocess.run(["kubectl", "kustomize", str(staging)], check=True, capture_output=True, text=True)
        if "vellumhub.test" in result.stdout or "REPLACE_WITH_" in result.stdout:
            parser.error("rendered manifests still contain staging placeholders")
        print(result.stdout, end="")


if __name__ == "__main__":
    main()
