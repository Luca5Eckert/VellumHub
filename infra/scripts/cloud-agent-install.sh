#!/usr/bin/env bash
#
# Cloud Agent one-time setup for the VellumHub monorepo.
#
# Runs once after the repository is checked out (the environment `install`
# phase). It provisions the host toolchain, configures Docker so it works
# inside a nested Cloud Agent VM, warms the Maven build, and creates a local
# `.env` so `docker compose up` works out of the box.
#
# The script is idempotent: it is safe to re-run and never rewrites existing
# state (e.g. an existing `.env`). It does NOT start the Docker daemon or run
# the container stack; that is per-boot work handled by cloud-agent-start.sh.
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$REPO_ROOT"

echo "==> Installing system toolchain (maven, docker, fuse-overlayfs)"
sudo apt-get update -qq
sudo DEBIAN_FRONTEND=noninteractive apt-get install -y -qq \
  maven docker.io docker-compose-v2 fuse-overlayfs

echo "==> Configuring the Docker daemon for the nested VM"
# The nested VM kernel rejects native overlayfs mounts, so use the userspace
# fuse-overlayfs storage driver instead of the containerd snapshotter.
sudo mkdir -p /etc/docker
echo '{"storage-driver":"fuse-overlayfs","features":{"containerd-snapshotter":false}}' \
  | sudo tee /etc/docker/daemon.json >/dev/null

echo "==> Pinning the docker-java API version for Testcontainers"
# Docker Engine >= 25 requires API >= 1.44, but the docker-java client bundled
# with Testcontainers 1.20.x can default to the (rejected) legacy API 1.32.
# Pinning it as a global JVM system property keeps every forked test JVM happy.
echo 'export _JAVA_OPTIONS="-Dapi.version=1.44"' \
  | sudo tee /etc/profile.d/vellumhub-env.sh >/dev/null
sudo chmod +x /etc/profile.d/vellumhub-env.sh
grep -q '^ryuk.disabled' "${HOME}/.testcontainers.properties" 2>/dev/null \
  || printf 'ryuk.disabled=true\n' >> "${HOME}/.testcontainers.properties"

echo "==> Enabling container-to-container networking on the docker bridge"
# Same-bridge traffic is L2-switched; routing it through nftables drops it in
# the nested VM. Persist the sysctl so it survives reboots (start.sh re-applies
# it at runtime because sysctl.d is not replayed inside the VM).
printf 'net.bridge.bridge-nf-call-iptables = 0\nnet.bridge.bridge-nf-call-ip6tables = 0\n' \
  | sudo tee /etc/sysctl.d/99-vellumhub-docker.conf >/dev/null

echo "==> Creating a local .env (only if missing)"
if [ ! -f .env ]; then
  JWT_KEY="$(openssl rand -base64 32)"
  cat > .env <<EOF
POSTGRES_USER=vellum
POSTGRES_PASSWORD=vellum_local_password
POSTGRES_DB=vellumhub
POSTGRES_HOST=localhost
POSTGRES_PORT=5432

JWT_KEY=${JWT_KEY}
JWT_EXPIRATION_MS=604800000
GOOGLE_CLIENT_ID=local-dev-google-client-id.apps.googleusercontent.com

CORS_ALLOWED_ORIGINS=http://localhost:3000

JAVA_TOOL_OPTIONS=
SHUTDOWN_TIMEOUT=30s
EOF
  echo "    wrote .env with a freshly generated JWT_KEY"
else
  echo "    .env already exists, leaving it untouched"
fi

echo "==> Building all modules (shared lib first, then services)"
# The root reactor installs lib/kafka-contracts before the services that depend
# on it. Tests are skipped here because they need the Docker daemon, which is
# only started per-boot by cloud-agent-start.sh.
mvn -B -ntp -DskipTests install

echo "==> install complete"
