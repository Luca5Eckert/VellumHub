# CI/CD workflow

VellumHub separates validation from publication.

- `Pull request validation` runs on pull requests to `main`. It executes the Maven reactor, Testcontainers integration tests, builds each application image, and scans every image with Trivy. Pull requests never publish images.
- `Publish validated images` runs after a push to `main`. It repeats the same quality gates and publishes only images that pass the Maven build and Trivy critical-vulnerability scan.
- `CodeQL` runs independently on pull requests and pushes to `main` for Java static analysis.

Published images use immutable commit tags:

```text
ghcr.io/luca5eckert/vellumhub-gateway:<git-sha>
ghcr.io/luca5eckert/vellumhub-user:<git-sha>
ghcr.io/luca5eckert/vellumhub-catalog:<git-sha>
ghcr.io/luca5eckert/vellumhub-engagement:<git-sha>
ghcr.io/luca5eckert/vellumhub-recommendation:<git-sha>
```

The `main` tag is published as a convenience only. Deployments must reference the commit-SHA tag or an image digest. Each image is labeled with its source repository, revision, title, and build metadata. The publication workflow uploads one image-reference artifact per service.

## Required checks

Configure branch protection for `main` in GitHub with these required checks:

- `Maven verify`
- the five `Build and scan` matrix entries
- `Analyze Java`

Require branches to be up to date before merge and restrict direct pushes to `main` according to the repository's maintainer policy. GitHub branch protection is repository configuration and cannot be versioned in this repository.

## Local reproduction

```bash
mvn -B -ntp clean verify
docker compose build
```

Use `docker buildx build --load --file services/<service>/Dockerfile .` to reproduce an individual image build, then run Trivy against the resulting local tag when investigating a CI security failure.
