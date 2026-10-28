variable "regiao" {
  description = "Regiao da AWS. us-east-1 costuma ser a mais barata; confira os precos antes de subir."
  type        = string
  default     = "us-east-1"
}

variable "repositorio_github" {
  description = "Repositorio (dono/nome) autorizado a assumir o papel de deploy via OIDC."
  type        = string
  default     = "heldemfilipe/orders-api"
}

variable "branch_deploy" {
  description = "Unica branch cujo workflow pode assumir o papel de deploy."
  type        = string
  default     = "main"
}

variable "criar_provedor_oidc" {
  description = "Cria o provedor OIDC do GitHub. So pode existir um por conta: se ja existir, use false."
  type        = bool
  default     = true
}

variable "cidrs_permitidos" {
  description = "Quem pode chamar o load balancer. 0.0.0.0/0 deixa a API (sem autenticacao) aberta para a internet; para estudo, prefira o seu IP/32."
  type        = list(string)
  default     = ["0.0.0.0/0"]
}

variable "quantidade_tarefas" {
  description = "Tarefas Fargate em execucao. 0 desliga a API sem destruir o resto."
  type        = number
  default     = 1
}

variable "cpu" {
  description = "Unidades de CPU da tarefa Fargate (256 = 0,25 vCPU)."
  type        = number
  default     = 512
}

variable "memoria" {
  description = "Memoria da tarefa Fargate em MiB. A JVM usa 75% (MaxRAMPercentage), entao 512 fica apertado."
  type        = number
  default     = 1024
}
