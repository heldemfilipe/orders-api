# Aqui nasce so o "cofre". O valor (a connection string do Atlas) NAO passa pelo Terraform,
# para nao ir parar no state nem no repositorio:
#   aws secretsmanager put-secret-value --secret-id pedidos/mongodb-uri --secret-string "mongodb+srv://..."
resource "aws_secretsmanager_secret" "mongodb_uri" {
  name        = "pedidos/mongodb-uri"
  description = "Connection string do MongoDB Atlas (variavel MONGODB_URI da API)"

  # sem janela de recuperacao, o nome fica preso 30 dias depois do destroy e o proximo apply falha
  recovery_window_in_days = 0
}
