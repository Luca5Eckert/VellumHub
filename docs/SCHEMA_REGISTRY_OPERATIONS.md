# Issue #348 — Apicurio Schema Registry: escolha e operação

Stage: implementation branch. Acceptance requires CI green on its exact SHA, code review and manual verification. The Registry is not deployed to production.

## Storage decision and boundaries

Choose Apicurio Registry 3.1.7, SQL storage backed by PostgreSQL. KafkaSQL was considered: Apicurio Registry v3 requires an append-only journal topic (cleanup.policy=delete and retention.ms/bytes=-1), with its own resilience, backup and recovery obligations. The VellumHub local Kubernetes broker is a single Kafka StatefulSet and would couple the recovery of events to the recovery of the schema metadata itself. PostgreSQL is a deliberately independent failure domain for schema IDs and histories. No Redis dependency is introduced.

The dedicated PostgreSQL *container* is for local Compose, Kind and CI. A production PostgreSQL instance may be shared at infrastructure level, but the Registry must own its own logical database, credentials, backup policy and quota. Never share application-domain tables or database credentials.

The pilot #347 proved the Confluent ccompat v7 API on Apicurio with TopicRecordNameStrategy, BACKWARD_TRANSITIVE, auto.register.schemas=false and avro.remove.java.properties=true. The approved initial subject belongs to the future topic book-events.avro, not to the existing JSON book-events topic. Issue #349 owns topic creation, Outbox migration and consumers.

## Compose: independent activation, isolated project and network

docker-compose.schema-registry.yml is opt-in and can start independently of the six application containers and their environment variables. It uses its own `vellumhub-schema-registry` Compose project and `vellumhub-schema-registry-net` network. Running `down --remove-orphans` for the Registry must not remove containers in the main `vellum-hub` Compose project. For application-to-Registry communication, explicitly attach the Registry container to the application's network as described below; isolation is the default to prevent accidental cross-project deletion. The API is bound only to 127.0.0.1:18081. PostgreSQL does not publish port 5432; persistent data uses registry_postgres_data. This local Registry deliberately disables OIDC and must never be exposed publicly.

Local commands:

    export REGISTRY_DB_PASSWORD="$(openssl rand -hex 24)"
    docker compose -f docker-compose.schema-registry.yml config --quiet
    docker compose -f docker-compose.schema-registry.yml up -d --wait
    python3 tools/schema_registry/wait_ready.py http://127.0.0.1:18081/apis/ccompat/v7/subjects
    python3 tools/schema_registry/registry_gate.py check
    python3 tools/schema_registry/registry_gate.py publish --ephemeral --state /tmp/registry-ids.json
    python3 tools/schema_registry/registry_gate.py verify --state /tmp/registry-ids.json
    python3 tools/schema_registry/registry_gate.py negative

To let application containers running in the main `vellum-hub` project resolve `http://schema-registry:8080`, first start the main stack, then attach **only** the Registry container to the application's network:

    docker network connect vellum-hub_vellum-network "$(docker compose -f docker-compose.schema-registry.yml ps -q schema-registry)"

This optional network attachment is ephemeral: re-run it after recreating the Registry container. Without it, the standalone Registry remains accessible via the loopback-published port `127.0.0.1:18081`, but not from the application network. Never combine Registry and main Compose files through `-f` expecting a single project; they have intentionally different project identities. The Kubernetes Registry deployment uses its own Kustomize configuration and is unaffected.

Test persistence by restarting schema-registry and registry-postgres **without removing the volume**, then verifying IDs using the same state file. Use docker compose down without -v. The -v option intentionally destroys local historical schemas and must only be used in a disposable test environment.

Backup: stop new schema registrations, use pg_dump in custom format against registry-postgres, securely store and periodically test restoration into an isolated Registry PostgreSQL database. Do not restore into an active shared Registry or expose backups in CI artifacts. Recovery must compare original IDs after restoring; DNS and a running database alone are not proof.

## Kubernetes deployment

The Registry is optional and has a separate Kustomize tree under deploy/kubernetes/schema-registry. It is not added to the main VellumHub base or production application overlay automatically. The local Kind overlay provisions one PostgreSQL StatefulSet with PVC and a Registry Deployment, both behind ClusterIP Services. The official PostgreSQL image starts with root privileges to initialize and chown its data directory, then drops to its postgres user; the local-only StatefulSet must not remove CAP_CHOWN/CAP_SETUID/CAP_SETGID before this initialization. The Registry container remains non-root with dropped capabilities. **Version-specific probe policy (CI verified):** Apicurio image `3.1.7` responds HTTP **200** at `/health/ready` on **port 8080**; `/q/health/ready` returns HTTP **404**. This was verified in Compose CI on the actual pinned image, not inferred from version-agnostic documentation. Kubernetes uses dedicated unauthenticated Quarkus endpoints `/health/ready` and `/health/live` on port 8080, not the ccompat API (which requires a bearer token in production), and not management port 9000 (added in later releases). The Registry API remains ClusterIP-only. Review these probes when upgrading the Apicurio image.

Before running the local overlay, create the vellumhub namespace and a Kubernetes Secret called schema-registry-db. It must have exactly username, password and url keys; url points to jdbc:postgresql://registry-postgres:5432/registrydb. Generate secret values outside Git; do not paste passwords into shared logs or PRs.

    kubectl apply -f deploy/kubernetes/base/namespace.yaml
    # Provision schema-registry-db Secret from your local secret manager.
    kubectl apply -k deploy/kubernetes/schema-registry/overlays/local
    kubectl -n vellumhub rollout status statefulset/registry-postgres --timeout=8m
    kubectl -n vellumhub rollout status deployment/schema-registry --timeout=8m
    kubectl -n vellumhub port-forward svc/schema-registry 18081:8080

The production overlay excludes the in-cluster PostgreSQL entirely and requires an existing external DB with separate credentials in schema-registry-db and an OIDC issuer/client ID in schema-registry-auth. OIDC authentication **and Apicurio role-based authorization** default to enabled in the shared base and remain enabled in the production overlay. Applying base alone without an OIDC issuer must fail closed. Only the explicitly named Kind/local patch disables both; this avoids an accidentally unauthenticated Registry in another overlay. Configure the provider's sr-readonly, sr-developer and sr-admin roles in the access token; regular readers must never receive write/admin roles. A production deployment must prove read-only principals receive HTTP 403 on schema publication before exposure. Registry API stays ClusterIP-only; protected ingress/TLS, CNI policy enforcement, actual OIDC role mapping for readers versus publishers, network destinations and externally managed backup must be validated before deploying. A Kubernetes Secret is not itself an external secret manager. Never deploy the local unauthenticated overlay as production. One replica and PVC in Kind are not a high availability guarantee.

## Contract policy and controlled publication

The versioned allowlist is infra/schema-registry/contracts.json. The policy is BACKWARD_TRANSITIVE per subject. The gate compares historical fixtures with the PR base via git diff; a PR modifying, deleting or renaming an existing historical file is rejected, while new versioned fixtures may be added. The manifest and Schema Registry still validate new candidates. Positive and incompatible future v3 schemas are checked against the Registry, but not published as stable versions.

Use python3 tools/schema_registry/registry_gate.py check to validate local metadata, and publish --ephemeral to register only against loopback/disposable registries. Repeat publishing to verify idempotency. Verify checks schemas and IDs by version and by ID and detects unexpected history changes.

The production workflow schema-registry-publish.yml is manual, main-only, requires HTTPS, a scoped OIDC token, and environment schema-registry-release. Before enabling it configure environment required reviewers and allowed deployment branch; explicitly set SCHEMA_REGISTRY_RELEASE_ENABLED=true only in that protected environment. Without environment rules, a manually invoked workflow alone is not sufficient operational approval. The PR CI has no real Registry credentials. Kafka ACLs and Registry read/write authorization are independent; production authentication and role-specific 401/403 tests remain deployment-specific prerequisites. No user event payloads or credentials are logged.

## Negative tests and evidence boundaries

- Real Testcontainers Kafka + PostgreSQL + Apicurio prove a generated Avro writer, Registry restart with stable IDs, cold consumer of pre-restart bytes and failed new-serializer schema lookup during a Registry outage.
- Compose CI reuses the versioned manifest, rejects a transitive-incompatible schema, proves stability of IDs over two process restarts, and rejects unexpected versions.
- A real ephemeral Kind job applies the optional local overlay and checks StatefulSet/Deployment readiness and the Registry API; the static Kustomize gate checks both local and production rendering.
- A passing test for a test-only serializer is not proof that the production Catalog Outbox fails safely: that real relay integration belongs to #349.
- No claims of production deployment, exactly-once delivery, no downtime, measured throughput or configured stable-role ACLs are made without separate evidence.

Sources: Apicurio 3.1 configuration reference https://www.apicur.io/registry/docs/apicurio-registry/3.1.x/getting-started/assembly-config-reference.html ; KafkaSQL topic requirements https://www.apicur.io/registry/docs/apicurio-registry/3.1.x/getting-started/assembly-deploying-registry-operator.html ; #347 pilot https://github.com/Luca5Eckert/VellumHub/pull/351 ; #348 tracker https://github.com/Luca5Eckert/VellumHub/issues/348 .
