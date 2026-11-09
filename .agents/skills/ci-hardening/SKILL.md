---
name: ci-hardening
description: Review Java backend CI pipelines, dependency trust, permissions and test gates without silently weakening workflow protection.
---

# ci-hardening

## Trigger
GitHub Actions changes, CI failures, coverage gates, containers, release or publishing automation.

## Procedure
1. Identify workflow triggers, permissions, secrets scope, build matrix and what each required job actually tests.
2. Pin/restrict third-party actions and dependencies appropriately under repository policy; favor minimal token permissions, trusted contexts and safe fork PR handling.
3. Preserve repository-specific unit, integration, architecture, security, contract and coverage gates. Do not downgrade them to advisory to pass.
4. Distinguish flaky environment/setup from product failures using logs and exact SHA; rerun only when justified and keep the original failure visible.
5. Consider reproducible Maven builds, caching safety, OIDC federation for CI credentials where supported and Docker-resource requirements.
6. Confirm merge checks apply to the final PR head; do not equate one passing workflow with complete release verification.

## Output
Threat/gate inventory, proposed minimal change and exact workflow validation evidence.

## Reference
Adapted from https://github.com/github/awesome-copilot/tree/main/skills/github-actions-hardening/SKILL.md.
