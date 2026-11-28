#!/usr/bin/env bash
# A successful Terraform apply only proves that EC2 accepted the infrastructure.
set -euo pipefail
region="${1:?Usage: verify-node.sh AWS_REGION INSTANCE_ID}"
instance="${2:?Usage: verify-node.sh AWS_REGION INSTANCE_ID}"
ssm=(aws --region "$region" ssm)

for attempt in $(seq 1 60); do
  status="$("${ssm[@]}" describe-instance-information \
    --filters "Key=InstanceIds,Values=$instance" \
    --query 'InstanceInformationList[0].PingStatus' --output text)"
  [[ "$status" != Online ]] || break
  [[ "$attempt" -lt 60 ]] || { echo "Instance did not come online in SSM" >&2; exit 1; }
  sleep 5
done

command_id="$("${ssm[@]}" send-command --instance-ids "$instance" \
  --document-name AWS-RunShellScript \
  --parameters '{"commands":["set -eu","timeout 300 cloud-init status --wait","mountpoint -q /var/lib/rancher/k3s","systemctl is-active --quiet k3s","/usr/local/bin/k3s kubectl wait node/vellumhub-staging --for=condition=Ready --timeout=120s","/usr/local/bin/k3s kubectl -n kube-system rollout status deployment/traefik --timeout=120s"]}' \
  --query 'Command.CommandId' --output text)"
for attempt in $(seq 1 120); do
  status="$("${ssm[@]}" list-command-invocations --command-id "$command_id" \
    --instance-id "$instance" --query 'CommandInvocations[0].Status' --output text)"
  case "$status" in
    Success) echo "K3s node, data mount and Traefik are ready"; exit 0 ;;
    Failed|Cancelled|TimedOut|Cancelling) echo "SSM readiness command $command_id ended as $status" >&2; exit 1 ;;
  esac
  sleep 5
done
echo "SSM readiness command $command_id did not finish in time" >&2
exit 1
