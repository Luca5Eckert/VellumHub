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


## Measurable engineering outcomes — issue #345

The optional `tools/metrics/analyze_outcomes.py` derives a candidate report **only from
executed, matching-commit artifacts**. It consumes M07 raw events and freshness,
M10 per-profile paired ranking metrics and (when available) the M08 failure matrix.
It does not run a benchmark or manufacture missing results.

After the existing reference suites and collector complete:

```sh
python3 -m unittest discover -s tools/metrics -p 'test_analyze_outcomes.py' -v
python3 tools/metrics/analyze_outcomes.py --sha "$(git rev-parse HEAD)"
```

Reports: `target/metrics/achievement-outcomes.json` and
`target/metrics/achievement-outcomes.md`, retained in the scoped
`backend-metrics.yml` PR run (and manual runs). **Do not commit raw user IDs,
synthetic per-user rows, or private metadata.** All source identifiers are
omitted from derived reports other than commit SHA.

### Audited reference: 31 October 2026

Source: [GitHub Actions 41245745067](https://github.com/Luca5Eckert/VellumHub/actions/runs/41245745067)
at `e4322184d39bd46de5508438e5f23d0c18fe80bb` (not a measurement of this
PR's head).

- 90-event medium burst: **90/90 projected** and **90/90 visible**; observed
  projection throughput **137.57 events/s**, projection p95 **511.67 ms**;
  interaction-to-visible-ranking p95 **732.74 ms**. The sequential scenario
  shows a much slower first-event outlier: do not use the burst number as a
  blanket all-event SLA or maximum throughput.
- Authenticated reads of pre-materialized local state with three simulated
  unavailable upstream endpoints: **30/30 successful**, zero monitored HTTP
  attempts, read p95 **28.17 ms**. It does not measure write convergence during
  outage or recovery time.
- Paired **synthetic** ranking over 24 profiles: 70/30
  semantic/popularity blend beats popularity-only in **23/24** profiles
  (one tie, zero losses), but loses to semantic-only in **22/24**
  profiles (two ties). Thus the blend must **not** be represented as the
  best observed ranker; no online evaluation or production lift is implied.
- 16/16 idempotency tests passed, with categorized checks on rollback (3),
  redelivery (4), concurrency (1), revision gaps (4), identity collisions (2)
  and two further checks. Test counts are not counts of fault-injected events.

### Strongest resume candidates and still-missing measurements

- **Candidate: event-driven recommendation convergence.** Explain local
  projections and show 90-event burst p95 or event throughput, scoped to
  one controlled CI run and with the early sequential outlier disclosed.
- **Candidate: read autonomy during controlled upstream failures.** The
  30/30 read count and zero monitored outbound calls are already supported.
- **Candidate: idempotent projections and failure containment.** Show
  specific failure modes proven by tests. A stronger numeric fault-load
  story would need a separate controlled event-count experiment with
  duplicate effects, replay, backlog recovery and sustained load.
- **Not yet a performance result:** Kind gate detects business regression
  despite readiness, but there is no controlled release-time improvement.
- **Not yet measured:** maximum Kafka processing capacity, post-outage
  convergence time, production availability or an online recommendation
  quality uplift. Absence must remain absence, not zero.

Use these measurements to choose outcomes before rewriting resume bullets;
do not imply that more metrics automatically justify more claims.
