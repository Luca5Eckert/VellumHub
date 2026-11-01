#!/usr/bin/env python3
"""M18 full-stack: actual VellumHub Java images + local dependencies in kind.

CI only. Prints no Kubernetes Secrets. Timings are GitHub-runner-local, not
production SLOs. Fails closed if any of six apps or seven dependencies are unready.
"""
import argparse
import datetime as dt
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import time
import urllib.error
import urllib.request

SERVICES = ("auth-service", "gateway-service", "user-service",
            "catalog-service", "engagement-service", "recommendation-service")
INFRA = ("redis", "kafka", "user-postgres", "catalog-postgres",
         "engagement-postgres", "recommendation-postgres", "auth-postgres")
PREFIX = "ghcr.io/luca5eckert/vellumhub-"
NAMESPACE = "vellumhub"
OUT = Path("target/metrics/kubernetes-full-stack.json")
WORK = Path("target/metrics/m18-real-overlay")
RENDERED = Path("target/metrics/kubernetes-full-stack-rendered.yaml")


def command(*args, timeout=90, check=True):
    r = subprocess.run(args, text=True, stdout=subprocess.PIPE,
                       stderr=subprocess.PIPE, timeout=timeout)
    if check and r.returncode:
        # Avoid logging values of Secret environment variables.
        raise RuntimeError(f"Command {' '.join(args)} failed rc={r.returncode}: "
                           f"{r.stderr[-500:]}")
    return r


def k(*args, **kw):
    return command("kubectl", "-n", NAMESPACE, *args, **kw)


def get(kind, name):
    return json.loads(k("get", kind, name, "-o", "json").stdout)


def ready_counts(kind, name):
    data = get(kind, name)
    desired = data["spec"].get("replicas", 1)
    ready = data.get("status", {}).get("readyReplicas", 0)
    return desired, ready, data


def until_all(kind, names, deadline_seconds, t0):
    deadline = time.monotonic() + deadline_seconds
    completed = {}
    while time.monotonic() < deadline:
        for name in names:
            if name in completed:
                continue
            try:
                desired, ready, _ = ready_counts(kind, name)
                if desired and ready == desired:
                    completed[name] = {"ready": ready, "desired": desired,
                                       "seconds_since_apply": round(time.monotonic() - t0, 3)}
            except RuntimeError:
                pass
        if len(completed) == len(names):
            return completed
        time.sleep(3)
    missing = [x for x in names if x not in completed]
    raise RuntimeError(f"{kind} readiness exceeded {deadline_seconds}s: {missing}")


def probe_via_port_forward(name, remote_port, *, count=5):
    # Port-forward opens an authenticated local tunnel directly to the real
    # Kubernetes Service, not to a synthetic upstream or controller fixture.
    local_port = {"auth-service": 19001, "gateway-service": 18080,
                  "user-service": 18081, "catalog-service": 18082,
                  "engagement-service": 18083, "recommendation-service": 18084}[name]
    proc = subprocess.Popen(["kubectl", "-n", NAMESPACE, "port-forward",
                             f"svc/{name}", f"{local_port}:{remote_port}",
                             "--address=127.0.0.1"],
                            stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    try:
        results = []
        for path in ("/actuator/health/liveness", "/actuator/health/readiness"):
            successes = 0
            deadline = time.monotonic() + 40
            while time.monotonic() < deadline and successes < count:
                if proc.poll() is not None:
                    raise RuntimeError(f"Port-forward exited early: {name}")
                started = time.monotonic()
                try:
                    req = urllib.request.Request(f"http://127.0.0.1:{local_port}{path}")
                    with urllib.request.urlopen(req, timeout=4) as res:
                        if res.status == 200:
                            elapsed = (time.monotonic() - started) * 1000
                            results.append({"path": path, "ms": round(elapsed, 3)})
                            successes += 1
                            continue
                except (urllib.error.HTTPError, urllib.error.URLError,
                        TimeoutError, ConnectionError):
                    pass
                time.sleep(0.3)
            if successes != count:
                raise RuntimeError(f"{name} {path} {successes}/{count} successes")
        return results
    finally:
        proc.terminate()
        try:
            proc.wait(timeout=5)
        except subprocess.TimeoutExpired:
            proc.kill()
            proc.wait()


def assert_gateway():
    results = probe_via_port_forward("gateway-service", 8080, count=3)
    return len(results)


def elapsed_ms(t):
    return round((time.monotonic() - t) * 1000, 2)


def render(sha):
    if WORK.exists():
        shutil.rmtree(WORK)
    shutil.copytree("deploy/kubernetes", WORK)
    overlay = WORK / "overlays/local/kustomization.yaml"
    source = overlay.read_text(encoding="utf8")
    if source.count("newTag: REPLACE_WITH_GIT_SHA") != len(SERVICES):
        raise RuntimeError("Unpinned/missing six-app image tags in local Kustomize overlay")
    overlay.write_text(source.replace("newTag: REPLACE_WITH_GIT_SHA",
                                     "newTag: " + sha), encoding="utf8")
    # CI overlay wraps the same six SHA-pinned Java images, but isolates
    # short-lived OAuth test-client settings from the regular local/prod overlays.
    raw = command("kubectl", "kustomize", str(WORK / "overlays/ci"), timeout=60).stdout
    RENDERED.write_text(raw, encoding="utf8")
    return raw



def real_business_e2e(expect_gateway_failure=False):
    """Exercise actual Auth/OAuth and domain HTTP with fresh CI-only users."""
    job = "vellumhub-business-e2e"
    k("delete", "job", job, "--ignore-not-found=true", "--wait=true", timeout=75)
    k("apply", "-f", "deploy/kubernetes/tests/ci-business-e2e-job.yaml", timeout=45)
    deadline = time.monotonic() + 310
    while time.monotonic() < deadline:
        status = get("job", job).get("status", {})
        if status.get("succeeded", 0) == 1:
            if expect_gateway_failure:
                raise RuntimeError("Deliberately broken Gateway unexpectedly passed business E2E")
            output = k("logs", f"job/{job}", timeout=20).stdout
            marker = "Real Auth UUIDv7 -> User profile -> Gateway -> User/Catalog/Engagement/Recommendation and negative JWT checks: OK"
            if marker not in output:
                raise RuntimeError("Business E2E succeeded without required functional assertions")
            return {
                "status": "PASSED", "real_oauth_pkce": True,
                "real_auth_to_user_provisioning": True,
                "real_gateway_domains_checked": ["user", "catalog", "engagement", "recommendation"],
                "negative": ["reduced scope denied", "ID token denied", "malformed JWT denied",
                             "ADMIN mutation denied"],
                "ci_fixture": "ephemeral accounts, not Google live or production"
            }
        if status.get("failed", 0):
            output = k("logs", f"job/{job}", check=False, timeout=20).stdout
            if expect_gateway_failure and "Gateway->User did not accept token HTTP" in output:
                return {"status": "EXPECTED_REGRESSION_DETECTED",
                        "failed_assertion": "authenticated Gateway->User profile HTTP",
                        "health_check_alone_would_be_insufficient": True}
            raise RuntimeError(f"Real business E2E failed unexpectedly (tail): {output[-850:]}")
        time.sleep(3)
    raise RuntimeError("Business E2E exceeded 310s deadline")


def measure(sha, budget):
    report = {
        "metric_id": "M18", "schema_version": 3, "commit_sha": sha,
        "captured_at_utc": dt.datetime.now(dt.timezone.utc).isoformat(),
        "github_run_id": os.getenv("GITHUB_RUN_ID"), "status": "IN_PROGRESS",
        "scope": "six REAL Java services + seven local stateful dependencies in kind",
        "environment": "GitHub Actions ubuntu-latest 4-core / ephemeral kind v1.31.2",
        "application_deployed": True, "cloud_production": False,
        "limitations": ("One ephemeral runner/cluster, one replica of each Java app; "
                        "real readiness/liveness and authenticated business smoke; "
                        "no node-loss recovery, production SLO, "
                        "traffic concurrency or Argo CD GitOps.")
    }
    OUT.parent.mkdir(parents=True, exist_ok=True)
    started = time.monotonic()
    try:
        report["kubernetes_version"] = json.loads(command(
            "kubectl", "version", "-o", "json").stdout)["serverVersion"]["gitVersion"]
        render(sha)
        # Admission dry-run validates the real manifest bytes against the API.
        command("kubectl", "apply", "--dry-run=server", "-f", str(RENDERED), timeout=90)
        report["manifests"] = {"checked_deployments": 6, "api_validation": "passed"}
        command("kubectl", "apply", "-f", str(RENDERED), timeout=120)
        applied = time.monotonic()
        report["dependencies"] = until_all("statefulset", INFRA, 330, applied)
        report["deployments"] = until_all("deployment", SERVICES, 420, applied)
        report["cold_start_total_ms"] = elapsed_ms(applied)
        probes = {}
        for service in SERVICES:
            samples = probe_via_port_forward(
                service, 9001 if service == "auth-service" else 8080)
            probes[service] = {"responses": len(samples),
                               "successful": len(samples) == 10,
                               "latencies_ms": samples}
        report["probes"] = probes
        report["business_e2e"] = real_business_e2e()

        # B is a digest-distinct image of the same Java binary, generated
        # within Kind CI. The regression is intentionally in runtime config.
        stable_deployment = get("deployment", "gateway-service")
        stable_template = stable_deployment["spec"]["template"]
        stable_image = stable_template["spec"]["containers"][0]["image"]
        candidate_image = stable_image + "-m18-candidate"
        started_rollout = time.monotonic()
        k("set", "image", "deployment/gateway-service",
          f"gateway-service={candidate_image}")
        k("rollout", "status", "deployment/gateway-service", "--timeout=240s", timeout=255)
        expected, ready, deployed_b = ready_counts("deployment", "gateway-service")
        current_image = deployed_b["spec"]["template"]["spec"]["containers"][0]["image"]
        if expected != 1 or ready != 1 or current_image != candidate_image:
            raise RuntimeError("Gateway candidate B image was not Ready")
        report["rolling_update"] = {
            "duration_ms": elapsed_ms(started_rollout),
            "ready": ready, "image_a": stable_image, "image_b": candidate_image,
            "distinct_image_tags": stable_image != candidate_image,
            "http_successes": assert_gateway(),
            "business_e2e": real_business_e2e()
        }

        # Prove that readiness alone cannot catch a broken authenticated API:
        # run B with a deliberately unreachable User upstream.
        started_detection = time.monotonic()
        k("set", "env", "deployment/gateway-service",
          "USER_SERVICE_URL=http://unreachable-user.invalid:8080")
        k("rollout", "status", "deployment/gateway-service", "--timeout=240s", timeout=255)
        _, bad_ready, bad_template = ready_counts("deployment", "gateway-service")
        if bad_ready != 1:
            raise RuntimeError("Expected functional failure after Ready, got unready Gateway")
        detected = real_business_e2e(expect_gateway_failure=True)
        report["functional_regression"] = {
            **detected, "detection_duration_ms": elapsed_ms(started_detection),
            "ready_when_business_failed": bad_ready,
            "failed_candidate": candidate_image
        }

        # Restore the exact previously-approved A template, including the
        # ConfigMap-based URL; JSON merge patch replaces env arrays entirely.
        started_rollback = time.monotonic()
        k("patch", "deployment", "gateway-service", "--type=merge", "-p",
          json.dumps({"spec": {"template": stable_template}}))
        k("rollout", "status", "deployment/gateway-service", "--timeout=240s", timeout=255)
        _, after_rollback, recovered = ready_counts("deployment", "gateway-service")
        restored = recovered["spec"]["template"]["spec"]["containers"][0]
        original = stable_template["spec"]["containers"][0]
        if restored["image"] != stable_image or after_rollback != 1:
            raise RuntimeError("Did not restore approved Gateway image A")
        if next((x for x in restored["env"] if x["name"] == "USER_SERVICE_URL"), None) != \
                next((x for x in original["env"] if x["name"] == "USER_SERVICE_URL"), None):
            raise RuntimeError("Rollback did not restore original User URL")
        report["rollback"] = {
            "functional_failure_detected": True, "restored_image_a": stable_image,
            "duration_ms": elapsed_ms(started_rollback),
            "http_successes": assert_gateway(),
            "business_e2e_after_rollback": real_business_e2e()
        }

        old = json.loads(k("get", "pod", "-l", "app.kubernetes.io/name=gateway-service",
                           "-o", "json").stdout)["items"]
        active = [p["metadata"]["name"] for p in old
                  if p.get("status", {}).get("phase") == "Running"
                  and not p["metadata"].get("deletionTimestamp")]
        if len(active) != 1:
            raise RuntimeError(f"Pod replacement expected one gateway Pod, found {active}")
        started_recovery = time.monotonic()
        k("delete", "pod", active[0], "--wait=false")
        end = time.monotonic() + 240
        while time.monotonic() < end:
            current = json.loads(k("get", "pod", "-l", "app.kubernetes.io/name=gateway-service",
                                   "-o", "json").stdout)["items"]
            new_ready = [p for p in current if p["metadata"]["name"] != active[0]
                         and not p["metadata"].get("deletionTimestamp")
                         and any(c.get("ready") for c in
                                 p.get("status", {}).get("containerStatuses", []))]
            if new_ready:
                break
            time.sleep(2)
        else:
            raise RuntimeError("Java gateway replacement Pod did not become Ready")
        report["pod_recovery"] = {
            "duration_ms": elapsed_ms(started_recovery),
            "http_successes": assert_gateway()
        }
        report["status"] = "PASSED"
    except Exception as e:
        report["status"] = "FAILED"
        report["error"] = str(e)
        print(f"M18 full-stack failure: {str(e)}", file=sys.stderr)
    finally:
        report["elapsed_total_ms"] = elapsed_ms(started)
        OUT.write_text(json.dumps(report, indent=2) + "\n", encoding="utf8")
        print(f"M18 evidence: {OUT} status={report['status']}", flush=True)
    return 0 if report["status"] == "PASSED" else 1


if __name__ == "__main__":
    p = argparse.ArgumentParser()
    p.add_argument("--sha", required=True)
    p.add_argument("--budget", type=int, default=800)
    opt = p.parse_args()
    sys.exit(measure(opt.sha, opt.budget))
