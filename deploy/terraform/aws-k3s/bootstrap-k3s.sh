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

volume_id='__DATA_VOLUME_ID__'
device="/dev/disk/by-id/nvme-Amazon_Elastic_Block_Store_${volume_id//-/}"
for attempt in $(seq 1 120); do
  [[ ! -b "$device" ]] || break
  sleep 5
done
[[ -b "$device" ]] || { echo "K3s data volume was not attached" >&2; exit 1; }

filesystem="$(blkid -s TYPE -o value "$device" || true)"
if [[ -z "$filesystem" ]]; then
  signatures="$(wipefs --noheadings --output TYPE "$device")"
  [[ -z "$signatures" ]] || { echo "Unrecognized signatures on the K3s data volume" >&2; exit 1; }
  mkfs.ext4 "$device"
elif [[ "$filesystem" != ext4 ]]; then
  echo "Expected ext4 on the K3s data volume" >&2
  exit 1
fi

uuid="$(blkid -s UUID -o value "$device")"
mountpoint=/var/lib/rancher/k3s
install -d -m 0700 "$mountpoint"
entry="UUID=$uuid $mountpoint ext4 defaults,nofail 0 2"
grep -Fxq "$entry" /etc/fstab || printf '%s\n' "$entry" >>/etc/fstab
systemctl daemon-reload
mountpoint -q "$mountpoint" || mount "$mountpoint"

cat >/etc/systemd/system/k3s.service <<'UNIT'
[Unit]
Description=K3s Kubernetes server
Wants=network-online.target
After=network-online.target
RequiresMountsFor=/var/lib/rancher/k3s

[Service]
Type=notify
Delegate=yes
KillMode=process
Restart=on-failure
RestartSec=5s
ExecStartPre=/usr/bin/mountpoint -q /var/lib/rancher/k3s
ExecStart=/usr/local/bin/k3s server --node-name vellumhub-staging --write-kubeconfig-mode=0600

[Install]
WantedBy=multi-user.target
UNIT

systemctl daemon-reload
systemctl enable --now k3s
