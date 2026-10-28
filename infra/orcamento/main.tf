# Alerta de custo. Fica numa pasta a parte, com state proprio, para o `terraform destroy` da
# infraestrutura da API nao apagar o alerta.

terraform {
  required_version = ">= 1.9"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 6.0"
    }
  }
}

# o Budgets e um servico global; a regiao so define o endpoint da API
provider "aws" {
  region = "us-east-1"
}

variable "email" {
  description = "Para onde vao os avisos."
  type        = string
}

variable "limite_usd" {
  description = "Orcamento mensal em dolares."
  type        = number
  default     = 10
}

resource "aws_budgets_budget" "mensal" {
  name         = "orders-api-mensal"
  budget_type  = "COST"
  limit_amount = tostring(var.limite_usd)
  limit_unit   = "USD"
  time_unit    = "MONTHLY"

  # gasto real passou de 50% e de 80% do limite
  dynamic "notification" {
    for_each = [50, 80]

    content {
      comparison_operator        = "GREATER_THAN"
      threshold                  = notification.value
      threshold_type             = "PERCENTAGE"
      notification_type          = "ACTUAL"
      subscriber_email_addresses = [var.email]
    }
  }

  # previsao de passar do limite ate o fim do mes
  notification {
    comparison_operator        = "GREATER_THAN"
    threshold                  = 100
    threshold_type             = "PERCENTAGE"
    notification_type          = "FORECASTED"
    subscriber_email_addresses = [var.email]
  }
}
