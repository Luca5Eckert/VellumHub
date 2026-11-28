#!/usr/bin/env bash
set -euo pipefail

version='v1.35.10+k3s1'
sha='5cd49f0faca5b2ea4355d4761e7b1445b8aa05cd50e612fd9c370551db49e5e2'
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

# EC2 associates the Elastic IP after boot has started, so the download retries.
curl --fail --silent --show-error --location --retry 18 --retry-delay 5 \
  --retry-all-errors --connect-timeout 5 --max-time 120 \
  "https://github.com/k3s-io/k3s/releases/download/${version}/k3s" -o "$work/k3s"
printf '%s  %s\n' "$sha" "$work/k3s" | sha256sum -c -
install -m 0755 "$work/k3s" /usr/local/bin/k3s

cat >/etc/systemd/system/k3s.service <<'UNIT'
[Unit]
Description=K3s Kubernetes server
Wants=network-online.target
After=network-online.target

[Service]
Type=notify
Delegate=yes
KillMode=process
Restart=on-failure
RestartSec=5s
ExecStart=/usr/local/bin/k3s server --write-kubeconfig-mode=0600

[Install]
WantedBy=multi-user.target
UNIT

systemctl daemon-reload
systemctl enable --now k3s
