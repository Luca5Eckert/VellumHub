output "instance_id" {
  description = "EC2 instance ID to use with Session Manager."
  value       = aws_instance.node.id
}

output "public_ipv4" {
  description = "Elastic IPv4 address for the future ingress DNS records."
  value       = aws_eip.node.public_ip
}
