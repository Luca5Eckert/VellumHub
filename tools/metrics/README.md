# Backend metrics evidence (M07-M10, M18)

This is a collector for **executed** benchmarks and JUnit reports, not a new
performance harness and not a claim that a CI check or production deployment passed.

## Run

1. Check out the exact SHA to measure; use Docker and the documented test environment.
2. Run both \`DistributedRecommendationBenchmarkIT\` and
   \`RecommendationFreshnessBenchmarkIT\` in separate Maven processes, with
   \`-Dbenchmark.profile=reference\` and \`-Dbenchmark.commitSha=$(git rev-parse HEAD)\`.
   Follow \`services/recommendation-service/benchmark/README.md\`.
3. Run \`RecommendationQualityEvaluationIT\`, passing
   \`-Devaluation.commitSha=$(git rev-parse HEAD)\`; follow the evaluation README.
4. Run the relevant security and Kafka distributed test suites. JUnit XML must be
   present under \`target/surefire-reports\`; absent/skipped tests are not successes.
5. Collect:
   \`\`\`sh
   python3 tools/metrics/collect_evidence.py --sha "$(git rev-parse HEAD)" --profile reference
   \`\`\`
6. For M18, provision an **ephemeral kind** cluster according to
   \`deploy/kubernetes/README.md\`, then:
   \`\`\`powershell
   ./deploy/kubernetes/scripts/measure-kubernetes.ps1
   \`\`\`
   This records readiness and gateway smoke only. No rollout time or rollback
   claim follows from observing an already-running deployment.

## Controls

- Output is local in \`target/metrics\`. Do not commit raw benchmark identifiers,
  tokens, user fields, AWS credentials, or environment-specific paths.
- A missing/mismatched SHA remains \`MISSING\` / \`SHA_MISMATCH\`, never zero.
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
