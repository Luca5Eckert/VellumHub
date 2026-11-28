mock_provider "aws" {
  mock_data "aws_ec2_instance_type" {
    defaults = {
      supported_architectures = ["x86_64"]
      ebs_nvme_support        = "required"
    }
  }

  mock_data "aws_ami" {
    defaults = {
      id = "ami-0123456789abcdef0"
    }
  }
}

variables {
  aws_region       = "us-east-1"
  ami_id           = "ami-0123456789abcdef0"
  instance_type    = "m7i.xlarge"
  web_ingress_cidr = "203.0.113.0/24"
}

run "staging_plan" {
  command = plan

  assert {
    condition = alltrue([
      for port, rule in aws_vpc_security_group_ingress_rule.web :
      rule.ip_protocol == "tcp" && rule.from_port == rule.to_port &&
      rule.from_port == tonumber(port) && rule.cidr_ipv4 == var.web_ingress_cidr
    ]) && toset(keys(aws_vpc_security_group_ingress_rule.web)) == toset(["80", "443"])
    error_message = "Only HTTP and HTTPS may be reachable from the configured CIDR."
  }

  assert {
    condition = (
      aws_instance.node.metadata_options[0].http_tokens == "required" &&
      aws_instance.node.metadata_options[0].http_put_response_hop_limit == 1 &&
      aws_instance.node.root_block_device[0].encrypted &&
      aws_instance.node.root_block_device[0].volume_type == "gp3"
    )
    error_message = "Require IMDSv2 and an encrypted gp3 volume."
  }

  assert {
    condition     = aws_ebs_volume.k3s_data.encrypted && aws_ebs_volume.k3s_data.type == "gp3"
    error_message = "K3s state must use a separately managed encrypted gp3 volume."
  }

  assert {
    condition     = aws_vpc.staging.cidr_block == "10.65.0.0/16" && aws_eip.node.domain == "vpc"
    error_message = "Use non-overlapping VPC addressing and an Elastic IP."
  }
}

run "reject_invalid_ami" {
  command = plan
  variables {
    ami_id = "replace-me"
  }
  expect_failures = [var.ami_id]
}
