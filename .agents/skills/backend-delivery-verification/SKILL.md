---
name: backend-delivery-verification
description: Validate implementation acceptance, tests and CI for the exact revision before calling a backend change complete.
---

# backend-delivery-verification

## Trigger
Issue implementation nearing completion, PR validation, CI fix or pre-merge review.

## Procedure
1. Read the issue and extract each acceptance criterion, including non-functional, security, migration and failure cases.
2. Build criterion -> source change -> test/check -> result matrix. Never say a criterion passed without evidence for that criterion.
3. Run focused tests and the repository-defined full validation. Record commands, environment, exact SHA and pass/fail/skip state.
4. Inspect architecture/security checks and affected integrations; classify gaps instead of pretending an unrelated successful job is proof.
5. Verify PR head SHA against CI SHA. Investigate failed or cancelled jobs and rerun the appropriate scope after changes.
6. Review final diff, generated files, new dependencies, secrets and documentation. Do not merge, publish or deploy without authorization.
7. Keep distinct: implemented, locally tested, CI-green, merged, deployed and operationally validated.
8. On blockers, provide smallest reproducible failure and next action rather than a blanket success message.

## Output
Acceptance matrix, validation commands/results, CI SHA, blockers and merge recommendation.

## Reference
Adapted from https://github.com/obra/superpowers/tree/main/skills/verification-before-completion/SKILL.md; complements existing workflow.
