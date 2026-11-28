# AWS staging infrastructure

This configuration plans one amd64 EC2 instance in a dedicated VPC, with a public subnet, an Elastic IP, an encrypted gp3 root volume and a separate protected gp3 data volume and a pinned K3s installation. The node uses Session Manager for administration. The Security Group admits only TCP 80 and 443 from the CIDR supplied by the operator. The subnet and VPC use a different address range from the default K3s pod and service networks.

## Review a plan

Use Terraform 1.11 or compatible, an AWS CLI authenticated to the intended account, and a reviewed Canonical Ubuntu 24.04 amd64 AMI for the chosen region. Select an instance type after reviewing its price and the capacity required by Kubernetes, the application workloads and any node-local dependencies. EC2, EBS, public IPv4, snapshots and outbound traffic can incur charges. Use a restricted operator CIDR for the first rehearsal.

Create a local `staging.tfvars` file and fill in the actual values.

```hcl
aws_region       = "<region>"
ami_id           = "<Ubuntu AMI for that region>"
instance_type    = "<approved amd64 instance type>"
web_ingress_cidr = "<operator public IPv4 CIDR>"
```

```bash
terraform init
terraform fmt -check -recursive
terraform validate
terraform test
terraform plan -var-file=staging.tfvars -out=staging.tfplan
terraform show staging.tfplan
```

The tests use a mocked AWS provider. The last two commands are for the selected AWS account. Review the plan, expected cost and permissions before applying it. This directory uses local state for an individual rehearsal. Keep state and plan files private. Configure an encrypted, access-controlled remote backend with state locking before sharing the environment with collaborators.

## Inspect the provisioned node

After applying the reviewed plan, run `bash verify-node.sh <region> "$(terraform output -raw instance_id)"`. It requires permission to inspect SSM instances, send the readiness command and read its status. Continue once the command confirms the data mount, K3s and Traefik. To inspect the node manually, use the Session Manager plugin and an operator IAM identity authorized for the instance.

```bash
aws ssm start-session --region <region> --target "$(terraform output -raw instance_id)"
```

Inside the session:

```bash
sudo cloud-init status --wait
sudo systemctl status k3s
sudo k3s kubectl get nodes
sudo k3s kubectl get pods -n kube-system
```

For remote Kubernetes administration, establish an SSM port forwarding session to port 6443 and use the node's kubeconfig with its loopback server address. Protect the kubeconfig as a cluster-admin credential.

Before deploying VellumHub, prepare an environment overlay with the actual HTTPS domains, issuer, callback, CORS, secrets and six verified image digests. The [Kind staging overlay](../../kubernetes/overlays/staging/README.md) demonstrates the application smoke test with test hosts and certificates. Repeat that test on the AWS environment using its actual endpoints.

This is a single-node rehearsal. The root volume is disposable. K3s and local-path volumes live on the separate EBS volume, which Terraform protects from destruction. Use an amd64 Nitro instance because bootstrap identifies the volume by its NVMe EBS ID. A bootstrap change that replaces the EC2 requires a maintenance window. Back up databases and application data, stop writes, stop K3s, flush and unmount the filesystem, then review the replacement plan. Create an EBS snapshot and practice restoring it onto a separate node before keeping durable data. EBS is AZ-bound and this workflow does not provide node failover.

## Render the application manifests

From the repository root, substitute the real DNS hosts and the frontend's registered OAuth callback:

```bash
python3 deploy/kubernetes/scripts/render-aws-staging.py \
  --auth-host auth.your-domain.com --api-host api.your-domain.com \
  --callback https://web.your-domain.com/oauth/callback > aws-staging.yaml
```

Create DNS records for the Auth and API hosts pointing to the environment's Elastic IP. Provide a TLS certificate covering both names in the `vellumhub-aws-tls` Secret. Provision the namespace, `vellumhub-secrets`, `vellumhub-jwt-private` and `vellumhub-jwt-public` using the same keys as the Kubernetes staging contract, with credentials from the environment's secret manager. Point kubectl at the AWS K3s context and run `kubectl apply --dry-run=server -f aws-staging.yaml` before applying anything.

The render reuses the single-node staging stack and the existing image digests. Its eight stateful dependencies are local to the EC2 instance. This render adds default-deny policies and explicit paths for Traefik, DNS, JWKS and the internal application dependencies. It assumes the default K3s labels for CoreDNS and Traefik and uses the local plaintext Kafka broker. Validate the policies on the destination CNI before exposing traffic.

The AWS OAuth client ID defaults to `vellumhub-aws-staging`. Keep it stable for the same callback. To change an already registered callback, render with a new `--client-id`, coordinate the Auth and frontend cutover, and validate PKCE and logout. Keep the previous manifests and client settings for rollback. Retire the old registration through the authorized Auth maintenance process after confirming the new client, without deleting existing token/session rows as part of deployment.
