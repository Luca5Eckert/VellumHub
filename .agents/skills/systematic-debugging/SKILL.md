---
name: systematic-debugging
description: Use for failing tests, broken pipelines, regressions, performance anomalies or intermittent distributed failures.
---
# Systematic debugging

1. Preserve the complete failure symptom, environment, commit, expected behavior and reproduction command.
2. Reproduce the smallest failing case. If the failure cannot be reproduced, distinguish a hypothesis from verified evidence.
3. Trace the failing path, including logs, SQL, event headers, timing, dependencies and recent relevant diffs; form competing hypotheses.
4. Design a discriminating experiment or regression test before changing code. Use the smallest intervention that explains the evidence.
5. Re-run the failing case and closely related tests; then run required higher-level checks and examine unintended changes.
6. If CI remains red, identify the exact remaining error. Do not change production behavior merely to accommodate a broken test assumption without verifying the contract.
7. Report root cause, proof, correction, validation and uncertainty. Never say resolved from a speculative patch.
