# Compose layout and command contracts

The default application stack is `compose.yaml` in the repository root.
Docker Compose automatically finds it, so ordinary commands continue to use
`docker compose up -d` from the root.

The following files are intentionally separated from the default stack:

| Path | Mode | Purpose |
| --- | --- | --- |
| `infra/compose/kafka-secure.yaml` | Overlay | Local Kafka SASL/ACL harness |
| `infra/compose/oidc-local.yaml` | Overlay | PKCE/identity integration harness |
| `infra/compose/google-federation.yaml` | Overlay | Google federation, layered after OIDC |
| `infra/compose/schema-registry.yaml` | Standalone | SQL Registry with isolated project/network |
| `infra/compose/tests/auth-poc.yaml` | Standalone | Minimal Auth/PostgreSQL security PoC |

Overlay commands must list the root Compose file **first**. This preserves
relative paths for bind mounts, build contexts and local secrets:

```bash
docker compose -f compose.yaml -f infra/compose/kafka-secure.yaml config --quiet
docker compose -f compose.yaml -f infra/compose/oidc-local.yaml config --quiet
docker compose -f compose.yaml -f infra/compose/oidc-local.yaml -f infra/compose/google-federation.yaml config --quiet
```

The last two commands require their documented OIDC environment variables.
For standalone stacks, explicitly select the root project directory:

```bash
REGISTRY_DB_PASSWORD='<local-secret>' docker compose --project-directory . -f infra/compose/schema-registry.yaml config --quiet
docker compose --project-directory . -f infra/compose/tests/auth-poc.yaml --profile auth-poc config --quiet
```

The Auth PoC additionally requires its local RSA keys, run-as UID/GID and
fixture credentials before execution. See
`docs/SECURITY_OIDC_LOTE2.md` and the `auth-poc.yml` workflow.

**Data safety:** the Registry retains explicit project name
`vellumhub-schema-registry`, network `vellumhub-schema-registry-net`
and volume `registry_postgres_data` within that project. The main
project retains `vellum-hub` and its existing named volumes.
Do not use `down -v` on non-disposable environments. Moving file paths
must not be treated as a volume migration.

IntelliJ's `.idea/` directory is local, ignored by the root `.gitignore`.
No project-specific IDE metadata is needed to compile or run the reactor.
