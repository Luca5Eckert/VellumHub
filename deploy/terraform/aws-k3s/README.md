# AWS staging infrastructure

This configuration plans one amd64 EC2 instance in a dedicated VPC, with a public subnet, an Elastic IP, an encrypted gp3 root volume and a pinned K3s installation. The node uses Session Manager for administration. The Security Group admits only TCP 80 and 443 from the CIDR supplied by the operator. The subnet and VPC use a different address range from the default K3s pod and service networks.

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

After the reviewed plan has been applied, use the Session Manager plugin and an operator IAM identity authorized for the instance.

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

This is a single-node rehearsal. The root volume and any node-local K3s data on it are deleted on instance termination, including replacement triggered by a bootstrap change. If the node hosts application databases or persistent volumes, establish and test backup and restore before storing data you need to keep.
