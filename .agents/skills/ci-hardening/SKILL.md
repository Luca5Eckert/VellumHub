---
name: ci-hardening
description: Change or review VellumHub CI/CD — GitHub Actions workflows, path filters, permissions and secrets, the per-service Docker image build (infra/docker/java/Dockerfile dependency stages), image verification matrices, CodeQL, architecture and contract gates. Use it when a workflow fails or is edited, a lib/ module is added, files referenced by workflow path filters move, image builds break, or someone proposes making a gate optional to get green.
---

# CI hardening (VellumHub)

CI gates are the project's memory of past failures. Weakening one to pass a build trades a visible red for an invisible regression, so fix the cause or document a reviewed exception.

## Procedure
1. For each affected workflow, identify triggers (including `paths`), permissions, secret scope and what each job actually proves.
2. Keep third-party actions pinned/restricted, token permissions minimal and fork PRs safe.
3. Preserve unit, integration, architecture, security, contract and coverage gates; never downgrade them to advisory.
4. Separate environment or setup failures from product failures using logs and the exact SHA; rerun only with a reason and keep the original failure visible.
5. Confirm merge checks apply to the final PR head; one green workflow is not release verification.

## Repository specifics
- Images build per service from `infra/docker/java/Dockerfile` with dependency stages (`deps-jwt`, `deps-kafka`, `deps-avro`) chosen by `DEPS_STAGE` in `compose.yaml` and `.github/workflows/verify-images.yml`. A new `lib/` module needs its own stage plus matching matrix and Compose entries.
- Path-filtered workflows stop triggering silently when watched files move (for example `kafka-service-acls.yml` watches `OrderedRecoveryPublisherConfig.java`); update `paths` in the same change.
- CI uses Temurin JDK 21. `architecture-fitness.yml` runs Python checks via `generate_evidence.py --run-static` and the ArchUnit suites; new `tools/architecture/test_*.py` files are picked up automatically.

## Output
Gate and threat inventory, minimal proposed change, and validation evidence tied to the workflow run and SHA.

Adapted from github/awesome-copilot `github-actions-hardening`.
