# Backend metrics evidence (M07-M10, M18)

This is a collector for **executed** benchmarks and JUnit reports, not a new
performance harness and not a claim that a CI check or production deployment passed.

## Run

1. Check out the exact SHA to measure; use Docker and the documented test environment.
2. Run both `DistributedRecommendationBenchmarkIT` and
   `RecommendationFreshnessBenchmarkIT` in separate Maven processes, with
   `-Dbenchmark.profile=reference` and `-Dbenchmark.commitSha=$(git rev-parse HEAD)`.
   Follow `services/recommendation-service/benchmark/README.md`.
3. Run `RecommendationQualityEvaluationIT`, passing
   `-Devaluation.commitSha=$(git rev-parse HEAD)`; follow the evaluation README.
4. Run `bash tools/metrics/run-contract-suites.sh` for M08/M09. The runner
   writes a SHA/time-bound marker only when Maven succeeds. JUnit XML must be
   from that execution; absent/skipped tests are not successes.
5. Collect:
   ```sh
   python3 tools/metrics/collect_evidence.py --sha "$(git rev-parse HEAD)" --profile reference
   ```
6. M18 now has **two distinct measurement scopes**:
   - `.github/workflows/kubernetes-m18.yml` provisions an ephemeral
     kind cluster and runs `python3 deploy/kubernetes/scripts/benchmark-kind.py
     --sha "$(git rev-parse HEAD)"`. It server-validates the REAL
     6-deployment local Kustomize overlay without launching their Java images,
     then measures deployment/readiness, HTTP smoke, rolling update,
     deliberately invalid-image rollout, rollback and Pod replacement using
     a small **Nginx controller fixture**. Original JSON in
     `target/metrics/kubernetes.json`, with git SHA; artifacts retained.
     **Fixture timings must never be attributed to the six VellumHub apps.**
   - `deploy/kubernetes/scripts/measure-kubernetes.ps1` remains a separate
     manual observation of actual application deployments **only in a cluster
     where all real images, credentials and dependencies are already available**.
     It does not measure actual rollout/rollback duration.
   - Full-stack M18 (six Java deployments, backing services, authentication and
     user-facing smoke on current images) remains UNMEASURED until those exact
     images and a working full-stack environment exist. Do not infer readiness
     or end-to-end availability from the controller fixture.

## Controls

- Output is local in `target/metrics`. Do not commit raw benchmark identifiers,
  tokens, user fields, AWS credentials, or environment-specific paths.
- A missing/mismatched SHA remains `MISSING` / `SHA_MISMATCH`, never zero.
- M07 projection, freshness and read p95 are separate measurements. The
  freshness p95 for a 90-event burst must not be mislabeled as all-event p95.
- M08 counts only actual JUnit executions of the idempotency suite; **coverage
  of failure modes requires a separate test-case review**, not the test count alone.
- M09 counts security-boundary tests, not an assumed number of fully integrated
  Resource Servers. Use the existing Compose/OIDC E2E report for integration
  claims; do not equate a mocked controller route with a consumer API.
- M10 measures offline ranking quality on a synthetic corpus. Do not join its
  nDCG with the separate M07 p95 into a single benchmark.
- Reference artifacts must be retained with original CI run URL, SHA, build date
  and machine metadata before promoting any claim to the resume.
