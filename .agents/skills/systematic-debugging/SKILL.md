---
name: systematic-debugging
description: Investigate VellumHub failures by reproducing them and falsifying hypotheses before patching — failing or flaky tests, CI-only failures, compilation errors after a JDK or dependency change, authentication bugs, stuck or blocked Kafka partitions, revision gaps, database inconsistencies and performance regressions. Use it whenever something "doesn't work", "broke", "fails only in CI", or a first quick fix did not hold.
---

# Systematic debugging

Guessing produces patch loops. Reproducing first, then changing one factor at a time, finds the root cause faster and leaves a regression test behind.

## Procedure
1. Capture the exact command, revision, input, environment (JDK, Docker available or not) and failure signature. Decide whether it is reproducible or environmental.
2. Trace the flow to the first wrong observable state; compare with a known-good revision when one exists.
3. List a few competing hypotheses and the check that would falsify each. Read logs, Surefire reports, stack traces and configuration before editing anything.
4. Reproduce with the smallest failing test or command. Common VellumHub environment causes worth ruling out early: no Docker for Testcontainers suites, a JDK other than 21 with missing `annotationProcessorPaths` (Lombok symbols such as `log` not found), stale local SNAPSHOT artifacts (build with `-am`), and a BLOCKED row in `kafka_consumer_quarantine` pausing a partition.
5. Fix the root cause with minimal scope, keep or add a regression test, and rerun the original failing check.
6. If the cause is still unknown, stop patching and report the evidence and the next experiment.
7. Never disable gates, lower coverage, loosen security or skip integration tests to get green.

## Output
Symptom → reproduction → root cause (or ranked hypotheses) → patch → revalidation.

Adapted from obra/superpowers `systematic-debugging`.
