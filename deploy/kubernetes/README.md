# VellumHub on Kubernetes

This directory is the desired state for the five application services. It never builds source in the cluster and every image reference must be a published immutable Git SHA (or preferably a digest).

## Layout

- `base`: namespace, configuration interface, five ClusterIP services, ingress and hardened application Deployments.
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

## Production and GitOps

Create the `vellumhub-secrets` Secret through External Secrets/your managed secret system; Kubernetes Secrets are an interface, not a production secret manager. Configure the production ConfigMap patch with external RDS/MSK/ElastiCache endpoints. Do not apply local stateful resources in production.

Bootstrap Argo CD separately with least-privilege permissions to the `vellumhub` namespace, then apply `argocd/vellumhub.yaml`. It starts in manual sync mode deliberately. After a verified sync, opt into `automated: { prune: true, selfHeal: true }` through review; `prune` deletes resources removed from Git and needs deliberate ownership boundaries.

CI updates only the SHA/digest in an overlay `images` block. It receives no cluster-admin credential. Argo CD pulls Git, detects the change, and rolls out only ready Pods. The gateway PDB applies only to production’s two replicas; it is intentionally absent locally.

## Rollout and rollback

1. Commit image version A and sync Argo CD; verify `kubectl rollout status` and smoke tests.
2. Commit version B, sync, and confirm the deployed image with `kubectl get pods -n vellumhub -o jsonpath='{..image}'`.
3. Revert the Git commit that changed the image reference and sync Argo CD again.
4. Confirm all five rollouts and smoke tests are healthy.

The resource baseline is intentionally modest: gateway/user 512Mi request, catalog/engagement 640Mi, recommendation 1Gi because its embedding model has higher startup and heap demand. These are starting measurements for local operation, not production sizing; tune from JVM/container metrics before enabling HPA. HPA is deferred until Metrics Server and a measured baseline exist.
