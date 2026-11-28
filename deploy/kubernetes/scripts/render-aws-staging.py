#!/usr/bin/env python3
"""Render the rehearsed single-node overlay with real HTTPS hosts and callback."""
import argparse
from ipaddress import ip_address
import json
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
from urllib.parse import urlsplit


def domain(value):
    try:
        ip_address(value)
    except ValueError:
        pass
    else:
        raise argparse.ArgumentTypeError("Ingress requires a DNS hostname, not an IP address")
    labels = value.split(".")
    if (
        len(value) > 253
        or len(labels) < 2
        or not any(char.isalpha() for char in labels[-1])
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



APP_SERVICES = ("auth", "gateway", "user", "catalog", "engagement", "recommendation")


def network_policies():
    def pod(name):
        label = "app.kubernetes.io/name" if name.endswith("-service") else "app"
        return {"podSelector": {"matchLabels": {label: name}}}

    def policy(name, selector, rules):
        return {
            "apiVersion": "networking.k8s.io/v1",
            "kind": "NetworkPolicy",
            "metadata": {"name": name, "namespace": "vellumhub"},
            "spec": {"podSelector": selector, "policyTypes": [kind.title() for kind in rules], **rules},
        }

    yield policy("aws-default-deny", {}, {"ingress": [], "egress": []})
    kube_system = {"namespaceSelector": {"matchLabels": {"kubernetes.io/metadata.name": "kube-system"}}}
    yield policy("aws-dns", {}, {"egress": [{
        "to": [{**kube_system, "podSelector": {"matchLabels": {"k8s-app": "kube-dns"}}}],
        "ports": [{"port": 53, "protocol": protocol} for protocol in ("UDP", "TCP")],
    }]})

    def port(number):
        return [{"port": number, "protocol": "TCP"}]

    for service, number in (("auth-service", 9000), ("gateway-service", 8080)):
        yield policy(f"aws-ingress-{service}", pod(service)["podSelector"], {"ingress": [{
            "from": [{**kube_system, "podSelector": {"matchLabels": {"app.kubernetes.io/name": "traefik"}}}],
            "ports": port(number),
        }]})

    dependencies = {
        "auth": [("user-service", 8080), ("auth-postgres", 5432)],
        "gateway": [("redis", 6379), *[(f"{name}-service", 8080) for name in APP_SERVICES[2:]]],
    }
    for name in APP_SERVICES[2:]:
        dependencies[name] = [(f"{name}-postgres", 5432), ("kafka", 9092)]
    for name in ("catalog", "recommendation"):
        dependencies[name].append(("redis-recommendation-cache", 6379))
    for source, targets in dependencies.items():
        sender = f"{source}-service"
        if source != "auth":
            targets.append(("auth-service", 9000))
        yield policy(f"aws-egress-{source}", pod(sender)["podSelector"], {"egress": [
            {"to": [pod(target)], "ports": port(number)} for target, number in targets
        ]})
        for target, number in targets:
            yield policy(f"aws-{source}-to-{target}", pod(target)["podSelector"], {"ingress": [{
                "from": [pod(sender)], "ports": port(number),
            }]})


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--auth-host", type=domain, required=True)
    parser.add_argument("--api-host", type=domain, required=True)
    parser.add_argument("--callback", type=callback_url, required=True)
    parser.add_argument("--client-id", default="vellumhub-aws-staging",
                        help="stable OAuth client ID; use a new ID for a callback migration")
    args = parser.parse_args()
    if not re.fullmatch(r"[a-z0-9][a-z0-9-]{0,62}", args.client_id):
        parser.error("supply a lowercase OAuth client ID")
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
                "vellumhub-spa-poc": args.client_id,
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
    for policy in network_policies():
        print("---")
        print(json.dumps(policy))


if __name__ == "__main__":
    main()
