# VellumHub on Kubernetes

This directory is the desired state for the six application services (Auth, Gateway, User, Catalog, Engagement, Recommendation). It never builds source in the cluster and every image reference must be a published immutable Git SHA (or preferably a digest).

## Layout

- `base`: namespace, configuration interface, six ClusterIP services, ingress and hardened application Deployments.
- `overlays/local`: one replica and in-cluster PostgreSQL, pgvector, Redis and KRaft Kafka with PVCs.
- `overlays/prod`: stateless applications only; database, Kafka and Redis endpoints must point to managed/external services.
- `argocd`: pull-based GitOps application definition.

The four database Services preserve ownership: `user_db`, `catalog_db`, `engagement_db`, and `recommendation_db`. The latter runs a pgvector image; Flyway remains the only owner of application tables.

## Local bootstrap (kind)

Prerequisites: Docker, `kind`, `kubectl`, and `kustomize` (or `kubectl kustomize`). The images must already have been published by CI and be readable by the cluster. For private GHCR packages, create `imagePullSecrets` separately and attach it through an environment overlay.

```powershell
kind create cluster --name vellumhub
kubectl apply -f deploy/kubernetes/base/config/secret.example.yaml # copy/edit it first; never commit the edited file
kubectl apply -k deploy/kubernetes/overlays/local
kubectl rollout status deployment/gateway-service -n vellumhub --timeout=10m
kubectl get pods,services,ingress,pvc -n vellumhub
```

Install an NGINX Ingress controller using its documented kind installation, map `vellumhub.local` to `127.0.0.1`, then run:

```powershell
.\deploy\kubernetes\scripts\smoke-kubernetes.ps1
```

If ingress is not installed, access the gateway temporarily with `kubectl port-forward -n vellumhub service/gateway-service 8080:8080`; run only deployment and DNS parts of the smoke script until a gateway URL is available.

Destroy the local cluster with `kind delete cluster --name vellumhub`. This removes the cluster PVCs.

## Reproducible M18 Kind controller benchmark (CI)

[The M18 GitHub Actions workflow](../../.github/workflows/kubernetes-m18.yml)
can be run with `workflow_dispatch` on a branch that contains the workflow.
It provisions **ephemeral Kind v1.31.2**, renders the *real* local Kustomize
overlay, and validates all six application Deployment definitions with
`kubectl apply --dry-run=server` against a running API server.

Separately, `deploy/kubernetes/scripts/benchmark-kind.py` deploys a
**two-replica Nginx fixture**, performs 5/5 HTTP checks after readiness,
rolling update, attempted invalid-image update followed by `rollout undo`,
and Pod deletion/replacement. It stores actual stage measurements and the
exact checked-out SHA in `target/metrics/kubernetes.json`. This is a real
Kubernetes-controller test, **not an application deployment of VellumHub**.

[Successful reference run 41247467864](https://github.com/Luca5Eckert/VellumHub/actions/runs/41247467864),
SHA `973f862e1e294e72bd943c5b199733f9294f3606`,
[artifact 13059726983](https://github.com/Luca5Eckert/VellumHub/actions/runs/41247467864/artifacts/13059726983):

- real overlay: **six Deployments accepted server-side, zero scheduled**;
- fixture initial readiness: **4.32471 s**, 2/2 ready;
- fixture rolling update: **4.01475 s**, 2/2 ready;
- invalid-image rollout detected, then undo: **0.34140 s** while old replicas stayed healthy;
- deleted Pod replaced, 2/2 ready: **1.31254 s**;
- HTTP 200: **5/5 requests** at each of four phases.

The first run failed early because the local Kafka environment variables
contained unquoted comma-delimited strings; the strict Kubernetes API
interpreted extra fields. Those values were corrected in
`overlays/local/infrastructure.yaml` before the successful retest.

**Interpretation boundaries:** One CI runner and one sample per phase.
Rollback of the Nginx fixture is not the time to restore Kafka/Postgres,
rebuild an application image, reconcile Argo CD, recover a Spring JVM or
restore user traffic. Deploying all six Java images with their full backing
stack and testing authenticated routes remains a **separate unverified task**.
Do not put the fixture seconds into a resume bullet as production backend
rollout performance.

## Production and GitOps

Create the `vellumhub-secrets` Secret through External Secrets/your managed secret system; Kubernetes Secrets are an interface, not a production secret manager. Configure the production ConfigMap patch with external RDS/MSK/ElastiCache endpoints. Do not apply local stateful resources in production.

Bootstrap Argo CD separately with least-privilege permissions to the `vellumhub` namespace, then apply `argocd/vellumhub.yaml`. It starts in manual sync mode deliberately. After a verified sync, opt into `automated: { prune: true, selfHeal: true }` through review; `prune` deletes resources removed from Git and needs deliberate ownership boundaries.

CI updates only the SHA/digest in an overlay `images` block. It receives no cluster-admin credential. Argo CD pulls Git, detects the change, and rolls out only ready Pods. The gateway PDB applies only to production’s two replicas; it is intentionally absent locally.

## Rollout and rollback

1. Commit image version A and sync Argo CD; verify `kubectl rollout status` and smoke tests.
2. Commit version B, sync, and confirm the deployed image with `kubectl get pods -n vellumhub -o jsonpath='{..image}'`.
3. Revert the Git commit that changed the image reference and sync Argo CD again.
4. Confirm all six rollouts and smoke tests are healthy.

The resource baseline is intentionally modest: gateway/user 512Mi request, catalog/engagement 640Mi, recommendation 1Gi because its embedding model has higher startup and heap demand. These are starting measurements for local operation, not production sizing; tune from JVM/container metrics before enabling HPA. HPA is deferred until Metrics Server and a measured baseline exist.


### Security JWT/JWKS transition (#304, #305)

In draft PR #321, JWT verifiers fetch only trusted public RS256 keys from `JWT_JWKS_URI` and require matching `JWT_ISSUER`, `JWT_AUDIENCE`, `typ=at+jwt`, immutable UUID subject, `client_id`, `jti`, issued/expiry times and signature. Unknown kids and invalid token contexts fail closed. `JWT_CLOCK_SKEW_SECONDS` defaults to 30; key material is not accepted from token-provided URLs. Only `auth-service` signs access tokens and exposes the trusted JWKS; Gateway and domain services are Resource Servers. Legacy `user_id` is emitted only as a temporary identical compatibility claim; do not use it for new authorization decisions. Read [JWKS runbook](../../docs/SECURITY_JWKS_RFC9068.md) for configuration, key rotation, outage behavior and safe cutover. Not yet deployed.
