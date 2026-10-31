#!/usr/bin/env python3
"""M18: real kind/Kubernetes-controller benchmark; NOT a VellumHub app deployment.

Applies the real local Kustomize overlay with Kubernetes server-side DRY RUN,
then exercises a separately labelled, lightweight Nginx fixture with real
readiness, HTTP smoke, rolling update, intentionally failed update, rollback,
and Pod replacement. Reports controller timings, not Java app latency/SLOs.
"""
import argparse
import datetime as dt
import json
import os
import pathlib
import re
import subprocess
import sys
import time
import urllib.error
import urllib.request

NAMESPACE = "vellumhub-m18"
DEPLOYMENT = "controller-fixture"
IMAGE = "nginx:1.27-alpine"
OUTPUT = pathlib.Path("target/metrics/kubernetes.json")
FIXTURE = pathlib.Path("target/metrics/kubernetes-controller-fixture.yaml")
RENDERED = pathlib.Path("target/metrics/vellumhub-local-rendered.yaml")


def execute(*args, timeout=90, allow_failure=False):
    completed = subprocess.run(args, text=True, stdout=subprocess.PIPE,
                               stderr=subprocess.PIPE, timeout=timeout)
    if completed.returncode and not allow_failure:
        raise RuntimeError(f"{' '.join(args)} returned {completed.returncode}: "
                           f"{completed.stderr[-900:]}")
    return completed


def kubectl(*args, timeout=90, allow_failure=False):
    return execute("kubectl", *args, timeout=timeout, allow_failure=allow_failure)


def rollout(timeout_seconds=180):
    kubectl("-n", NAMESPACE, "rollout", "status",
            f"deployment/{DEPLOYMENT}", f"--timeout={timeout_seconds}s",
            timeout=timeout_seconds + 15)


def replicas():
    data = json.loads(kubectl("-n", NAMESPACE, "get", "deployment",
                              DEPLOYMENT, "-o", "json").stdout)
    return data.get("status", {}).get("readyReplicas", 0), data


def http_smoke():
    # The port-forward process is not an application service or a second proxy.
    # It tunnels directly to the fixture's Kubernetes ClusterIP Service.
    process = subprocess.Popen(
        ["kubectl", "-n", NAMESPACE, "port-forward", f"svc/{DEPLOYMENT}",
         "18080:80", "--address=127.0.0.1"],
        stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    try:
        deadline = time.monotonic() + 25
        successful = 0
        while time.monotonic() < deadline:
            if process.poll() is not None:
                raise RuntimeError("Kubernetes port-forward terminated early")
            try:
                with urllib.request.urlopen("http://127.0.0.1:18080/",
                                            timeout=3) as response:
                    if response.status == 200:
                        successful += 1
                        if successful == 5:
                            return successful
            except (urllib.error.URLError, TimeoutError, ConnectionError):
                pass
            time.sleep(0.4)
        raise RuntimeError(f"HTTP smoke incomplete: {successful}/5")
    finally:
        process.terminate()
        try:
            process.wait(timeout=5)
        except subprocess.TimeoutExpired:
            process.kill()
            process.wait()


def pods():
    data = json.loads(kubectl("-n", NAMESPACE, "get", "pods", "-l",
                              f"app={DEPLOYMENT}", "-o", "json").stdout)
    return data["items"]


def ready_pods():
    return [p for p in pods() if not p["metadata"].get("deletionTimestamp")
            and p.get("status", {}).get("phase") == "Running"
            and all(c.get("ready") for c in
                    p.get("status", {}).get("containerStatuses", []))
            and p.get("status", {}).get("containerStatuses")]


def measure(source_sha):
    data = {
        "schema_version": 2, "metric_id": "M18", "commit_sha": source_sha,
        "status": "FAILED",
        "timestamp_utc": dt.datetime.now(dt.timezone.utc).isoformat(),
        "environment": "GitHub Actions / ephemeral kind on Ubuntu / local Docker",
        "github_run_id": os.environ.get("GITHUB_RUN_ID"),
        "cluster_context": kubectl("config", "current-context").stdout.strip(),
        "kubernetes_version": json.loads(
            kubectl("version", "-o", "json").stdout)["serverVersion"]["gitVersion"],
        "workload_type": "isolated nginx fixture using actual Kubernetes controllers",
        "application_deployed": False,
        "production_claim": False,
        "limitations": ("Kustomize manifests of six VellumHub deployments are admission "
                        "checked via server-side dry-run only. Timings and HTTP checks "
                        "exercise a standalone Nginx fixture, NOT real Java applications, "
                        "their backing databases, SSO, full deploy, user traffic, "
                        "production capacity or GitOps/ArgoCD reconciliation.")
    }

    # Validate the real repository overlays against an actual Kubernetes API
    # without scheduling six application images or using credentials.
    namespace = "vellumhub"
    kubectl("apply", "-f", "deploy/kubernetes/base/namespace.yaml")
    rendered = kubectl("kustomize", "deploy/kubernetes/overlays/local",
                       timeout=60).stdout
    RENDERED.write_text(rendered, encoding="utf-8")
    docs = re.split(r"(?m)^---\s*$", rendered)
    deployments = [doc for doc in docs if re.search(
        r"(?m)^kind:\s*Deployment\s*$", doc)]
    if len(deployments) != 6:
        raise RuntimeError(f"Expected 6 app deployment manifests, got {len(deployments)}")
    for name in ("auth-service", "gateway-service", "user-service",
                 "catalog-service", "engagement-service",
                 "recommendation-service"):
        if not any(re.search(r"\b" + re.escape(name) + r"\b", doc)
                   for doc in deployments):
            raise RuntimeError(f"Missing deployment {name}")
    kubectl("apply", "--dry-run=server", "-f", str(RENDERED), timeout=90)
    data["manifest_validation"] = {
        "kind": "kustomize local overlay and API admission dry-run",
        "passed": True, "application_deployments_checked": 6,
        "deployments_started": 0
    }

    kubectl("create", "namespace", NAMESPACE)
    FIXTURE.write_text("""apiVersion: apps/v1
kind: Deployment
metadata:
  name: controller-fixture
  namespace: vellumhub-m18
spec:
  replicas: 2
  strategy:
    type: RollingUpdate
    rollingUpdate: {maxUnavailable: 0, maxSurge: 1}
  selector: {matchLabels: {app: controller-fixture}}
  template:
    metadata: {labels: {app: controller-fixture}}
    spec:
      containers:
      - name: web
        image: nginx:1.27-alpine
        imagePullPolicy: IfNotPresent
        ports: [{name: http, containerPort: 80}]
        readinessProbe: {httpGet: {path: /, port: http}, periodSeconds: 2, failureThreshold: 2}
        livenessProbe: {httpGet: {path: /, port: http}, periodSeconds: 5, failureThreshold: 3}
        resources:
          requests: {cpu: 10m, memory: 24Mi}
          limits: {cpu: 150m, memory: 128Mi}
---
apiVersion: v1
kind: Service
metadata: {name: controller-fixture, namespace: vellumhub-m18}
spec:
  selector: {app: controller-fixture}
  ports: [{port: 80, targetPort: http}]
""", encoding="utf-8")

    started = time.monotonic()
    kubectl("apply", "-f", str(FIXTURE))
    rollout()
    ready, _ = replicas()
    if ready != 2:
        raise RuntimeError(f"Initial deployment ready={ready}, wanted 2")
    data["initial_deploy"] = {
        "ready_replicas": ready,
        "duration_ms": round((time.monotonic() - started) * 1000, 2),
        "http_successful": http_smoke()
    }

    started = time.monotonic()
    kubectl("-n", NAMESPACE, "set", "env",
            f"deployment/{DEPLOYMENT}", "METRICS_REVISION=stable-two")
    rollout()
    ready, _ = replicas()
    if ready != 2:
        raise RuntimeError(f"Rolling update readiness={ready}, wanted 2")
    data["rolling_update"] = {
        "ready_replicas": ready,
        "duration_ms": round((time.monotonic() - started) * 1000, 2),
        "http_successful": http_smoke()
    }

    # Fail an attempted image update intentionally, then revert via the
    # Kubernetes Deployment revision history; verify old replicas remain ready.
    bad_image = "nginx:nonexistent-m18-invalid-tag"
    kubectl("-n", NAMESPACE, "set", "image",
            f"deployment/{DEPLOYMENT}", f"web={bad_image}")
    attempted = kubectl("-n", NAMESPACE, "rollout", "status",
                        f"deployment/{DEPLOYMENT}", "--timeout=25s",
                        timeout=50, allow_failure=True)
    if attempted.returncode == 0:
        raise RuntimeError("Invalid image unexpectedly rolled out")
    during_failure, _ = replicas()
    if during_failure < 2:
        raise RuntimeError(f"Failed update dropped ready replicas to {during_failure}")

    started = time.monotonic()
    kubectl("-n", NAMESPACE, "rollout", "undo",
            f"deployment/{DEPLOYMENT}")
    rollout()
    ready, deployment = replicas()
    image = deployment["spec"]["template"]["spec"]["containers"][0]["image"]
    if ready != 2 or image != IMAGE:
        raise RuntimeError(f"Rollback not restored, ready={ready}, image={image}")
    data["failed_update_rollback"] = {
        "failed_update_detected": True, "ready_during_failed_update": during_failure,
        "restored_image": image,
        "rollback_duration_ms": round((time.monotonic() - started) * 1000, 2),
        "http_successful_after_rollback": http_smoke()
    }

    before = ready_pods()
    if len(before) != 2:
        raise RuntimeError("Not enough ready pods before deletion")
    name = before[0]["metadata"]["name"]
    started = time.monotonic()
    kubectl("-n", NAMESPACE, "delete", "pod", name, "--wait=false")
    deadline = time.monotonic() + 180
    while time.monotonic() < deadline:
        fresh = ready_pods()
        if len(fresh) == 2 and all(p["metadata"]["name"] != name for p in fresh):
            break
        time.sleep(1)
    else:
        raise RuntimeError("Replacement Pod not ready before timeout")
    data["pod_recovery"] = {
        "ready_replicas": len(fresh),
        "duration_ms": round((time.monotonic() - started) * 1000, 2),
        "http_successful_after_recovery": http_smoke()
    }
    data["status"] = "PASSED"
    return data


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--sha", required=True)
    args = parser.parse_args()
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    try:
        result = measure(args.sha)
        return_code = 0
    except Exception as error:
        result = {
            "schema_version": 2, "metric_id": "M18",
            "commit_sha": args.sha, "status": "FAILED",
            "timestamp_utc": dt.datetime.now(dt.timezone.utc).isoformat(),
            "application_deployed": False,
            "error": str(error), "scope": "kind controller fixture / real overlay dry-run",
            "production_claim": False
        }
        return_code = 1
        print(f"M18 failed: {error}", file=sys.stderr)
    OUTPUT.write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
    print(f"M18 report: {OUTPUT} status={result['status']}")
    return return_code


if __name__ == "__main__":
    raise SystemExit(main())
