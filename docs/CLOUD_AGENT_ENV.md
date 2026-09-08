# Cloud Agent Development Environment

This document describes how the VellumHub monorepo is provisioned inside a
[Cursor Cloud Agent](https://cursor.com/docs/cloud-agent) VM. The same scripts
also work as a reference for reproducing a full local backend on a fresh Ubuntu
host.

## What the environment provides

A complete, runnable copy of the platform:

- **Java 21** and **Maven** for building and testing all five services plus the
  shared `lib/kafka-contracts` library.
- **Docker + Docker Compose**, configured to run inside the nested Cloud Agent
  VM, for the full 13-service local stack and for the Testcontainers-based
  integration tests.
- A generated local `.env` so `docker compose up` works out of the box.

## Lifecycle

| Phase | Script | Runs | Responsibility |
|---|---|---|---|
| `install` | [`infra/scripts/cloud-agent-install.sh`](../infra/scripts/cloud-agent-install.sh) | once (baked into the environment snapshot) | Install toolchain, configure Docker, warm the Maven build, create `.env`. |
| `start` | [`infra/scripts/cloud-agent-start.sh`](../infra/scripts/cloud-agent-start.sh) | every boot | Start the Docker daemon and apply runtime networking sysctls. |

## Nested-VM specifics

Running Docker inside the Cloud Agent VM requires three adjustments that the
scripts apply automatically:

1. **Storage driver.** The VM kernel rejects native `overlayfs` mounts, so the
   daemon uses the userspace `fuse-overlayfs` driver
   (`/etc/docker/daemon.json`) instead of the containerd snapshotter.
2. **Bridge networking.** Same-bridge container-to-container traffic is dropped
   when it is routed through nftables. Setting
   `net.bridge.bridge-nf-call-iptables=0` lets the bridge switch it at layer 2,
   which is required for Kafka ⇄ Zookeeper and the services ⇄ their databases.
3. **Testcontainers client API.** Docker Engine requires API ≥ 1.44, but the
   `docker-java` client bundled with Testcontainers 1.20.x can default to the
   rejected legacy API 1.32. The system property `-Dapi.version=1.44` is pinned
   globally via `_JAVA_OPTIONS` so every forked test JVM negotiates a supported
   API version.

## Common commands

```bash
# Build everything (shared lib first, then services)
mvn -B -ntp -DskipTests install

# Run the full test suite (unit, slice, and Testcontainers integration tests)
mvn -B -ntp test

# Bring up the full local stack (gateway on :8080, Kafka UI on :8090)
docker compose up -d

# Tear it down
docker compose down
```

See the root [`README.md`](../README.md) for the full architecture, service map,
and API surface.
