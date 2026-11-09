---
name: systematic-debugging
description: Investigate failing tests, CI failures and runtime defects by reproducing and falsifying hypotheses before patching.
---

# systematic-debugging

## Trigger
Unexpected output, flaky integration test, CI failure, authentication bug, database inconsistency, performance regression.

## Procedure
1. Capture the exact failing command, revision, input and failure signature. Establish whether the error is reproducible or environmental.
2. Trace the flow and identify the first incorrect observable state; compare a known-good baseline when available.
3. List minimal competing hypotheses and falsification checks. Inspect logs, test report, stack trace and config; avoid changing several unrelated factors.
4. Reproduce with the smallest failing test or command. If CI-only, inspect job context and distinguish credentials, infrastructure, timeouts and code regressions.
5. Fix the root cause with minimal scope; keep or add a regression test and rerun the original failing check.
6. Stop speculative patch loops. When the cause is still unknown, report collected evidence and a precise next experiment.
7. Never disable quality gates, reduce coverage, loosen security or mark integration tests skipped just to get green CI.

## Output
Symptom -> reproduced evidence -> root cause or hypotheses -> patch -> revalidation.

## Reference
Adapted from https://github.com/obra/superpowers/tree/main/skills/systematic-debugging/SKILL.md.
