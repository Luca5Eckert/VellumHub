#!/usr/bin/env bash
#
# Cloud Agent per-boot startup for the VellumHub monorepo.
#
# Runs on every boot (the environment `start` phase). It re-applies the runtime
# networking sysctl and brings the Docker daemon up, so that both the container
# stack (`docker compose up`) and the Testcontainers integration tests work.
#
# It is idempotent: if the daemon is already running it does nothing but fix up
# the socket permissions. It intentionally does NOT run `docker compose up` or
# the Maven build; starting the application stack is left to the developer.
set -euo pipefail

echo "==> Re-applying bridge networking sysctl"
sudo sysctl -w net.bridge.bridge-nf-call-iptables=0 net.bridge.bridge-nf-call-ip6tables=0 >/dev/null 2>&1 || true

if sudo docker info >/dev/null 2>&1; then
  echo "==> Docker daemon already running"
else
  echo "==> Starting the Docker daemon (fuse-overlayfs storage driver)"
  # setsid fully detaches dockerd into its own session so it survives this
  # start hook returning (a plain `nohup ... &` can still be torn down with the
  # hook's process group, leaving the boot with no daemon).
  sudo bash -c 'setsid dockerd >/var/log/dockerd.log 2>&1 </dev/null &'
  for _ in $(seq 1 60); do
    if sudo docker info >/dev/null 2>&1; then
      break
    fi
    sleep 1
  done
  if ! sudo docker info >/dev/null 2>&1; then
    echo "!! Docker daemon failed to start; see /var/log/dockerd.log" >&2
    tail -n 40 /var/log/dockerd.log >&2 || true
    exit 1
  fi
fi

# Allow the non-root user to talk to the daemon without sudo.
sudo chmod 666 /var/run/docker.sock 2>/dev/null || true

echo "==> Docker is ready:"
docker version --format '    client {{.Client.Version}} / server {{.Server.Version}} (storage: {{.Server.Driver}})' 2>/dev/null \
  || sudo docker version --format '    client {{.Client.Version}} / server {{.Server.Version}}'
echo "==> start complete"
