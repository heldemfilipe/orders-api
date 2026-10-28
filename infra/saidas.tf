output "url_api" {
  description = "URL publica da API (HTTP)."
  value       = "http://${aws_lb.api.dns_name}"
}

output "papel_deploy_arn" {
  description = "Vai na variavel de repositorio AWS_ROLE_ARN do GitHub."
  value       = aws_iam_role.deploy.arn
}

output "regiao" {
  description = "Vai na variavel de repositorio AWS_REGION do GitHub."
  value       = var.regiao
}

output "segredo_mongodb" {
  description = "Nome do segredo que recebe a connection string do Atlas."
  value       = aws_secretsmanager_secret.mongodb_uri.name
}
