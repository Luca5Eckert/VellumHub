variable "aws_region" {
  type        = string
  description = "AWS region for the rehearsal."
}

variable "ami_id" {
  type        = string
  description = "Reviewed Canonical Ubuntu 24.04 amd64 EBS AMI ID in aws_region, with SSM Agent available."
  validation {
    condition     = can(regex("^ami-[0-9a-f]{17}$", var.ami_id))
    error_message = "Supply a modern AWS AMI ID."
  }
}

variable "instance_type" {
  type        = string
  description = "Explicit amd64 EC2 instance type selected after reviewing its cost and capacity."
}

variable "root_volume_gib" {
  type        = number
  description = "Size in GiB for Ubuntu, images, and node-local K3s persistent volumes."
  default     = 80
  validation {
    condition     = var.root_volume_gib >= 40 && floor(var.root_volume_gib) == var.root_volume_gib
    error_message = "Use an integer volume size of at least 40 GiB."
  }
}

variable "web_ingress_cidr" {
  type        = string
  description = "IPv4 CIDR allowed to reach ports 80 and 443. Use an operator CIDR for the first rehearsal."
  validation {
    condition     = can(cidrnetmask(var.web_ingress_cidr))
    error_message = "Supply a valid IPv4 CIDR."
  }
}
