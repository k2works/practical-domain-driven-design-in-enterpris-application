output "ecr_repository_url" {
  value = module.ecr.repository_url
}

output "alb_dns_name" {
  value = module.application.alb_dns_name
}

output "cluster_name" {
  value = module.application.cluster_name
}

output "service_name" {
  value = module.application.service_name
}

output "bastion_instance_id" {
  description = "運用タスクの SSM の --target（.env の OPS_<ENV>_BASTION_ID）"
  value       = module.bastion.instance_id
}

output "database_endpoint" {
  value = module.database.endpoint
}

output "database_master_secret_arn" {
  value = module.database.master_secret_arn
}
