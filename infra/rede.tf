# VPC propria, so com subnets publicas e sem NAT gateway (que cobra por hora).
# As tarefas recebem IP publico para falar com ECR, Secrets Manager e Atlas; quem as protege
# e o security group, que so aceita trafego vindo do load balancer.

locals {
  zonas = ["${var.regiao}a", "${var.regiao}b"]
}

resource "aws_vpc" "principal" {
  cidr_block           = "10.0.0.0/16"
  enable_dns_hostnames = true
  enable_dns_support   = true

  tags = { Name = "pedidos" }
}

resource "aws_internet_gateway" "principal" {
  vpc_id = aws_vpc.principal.id

  tags = { Name = "pedidos" }
}

resource "aws_subnet" "publica" {
  count = length(local.zonas)

  vpc_id                  = aws_vpc.principal.id
  availability_zone       = local.zonas[count.index]
  cidr_block              = cidrsubnet(aws_vpc.principal.cidr_block, 8, count.index)
  map_public_ip_on_launch = true

  tags = { Name = "pedidos-publica-${local.zonas[count.index]}" }
}

resource "aws_route_table" "publica" {
  vpc_id = aws_vpc.principal.id

  route {
    cidr_block = "0.0.0.0/0"
    gateway_id = aws_internet_gateway.principal.id
  }

  tags = { Name = "pedidos-publica" }
}

resource "aws_route_table_association" "publica" {
  count = length(aws_subnet.publica)

  subnet_id      = aws_subnet.publica[count.index].id
  route_table_id = aws_route_table.publica.id
}

resource "aws_security_group" "alb" {
  name        = "pedidos-alb"
  description = "Entrada HTTP no load balancer"
  vpc_id      = aws_vpc.principal.id

  tags = { Name = "pedidos-alb" }
}

resource "aws_vpc_security_group_ingress_rule" "alb_http" {
  count = length(var.cidrs_permitidos)

  security_group_id = aws_security_group.alb.id
  cidr_ipv4         = var.cidrs_permitidos[count.index]
  from_port         = 80
  to_port           = 80
  ip_protocol       = "tcp"
}

resource "aws_vpc_security_group_egress_rule" "alb_para_tarefas" {
  security_group_id            = aws_security_group.alb.id
  referenced_security_group_id = aws_security_group.tarefa.id
  from_port                    = 8080
  to_port                      = 8080
  ip_protocol                  = "tcp"
}

resource "aws_security_group" "tarefa" {
  name        = "pedidos-tarefa"
  description = "API: aceita so o load balancer"
  vpc_id      = aws_vpc.principal.id

  tags = { Name = "pedidos-tarefa" }
}

resource "aws_vpc_security_group_ingress_rule" "tarefa_do_alb" {
  security_group_id            = aws_security_group.tarefa.id
  referenced_security_group_id = aws_security_group.alb.id
  from_port                    = 8080
  to_port                      = 8080
  ip_protocol                  = "tcp"
}

# saida liberada: ECR (443), Secrets Manager (443) e Atlas (27017) nao tem IP fixo
resource "aws_vpc_security_group_egress_rule" "tarefa_saida" {
  security_group_id = aws_security_group.tarefa.id
  cidr_ipv4         = "0.0.0.0/0"
  ip_protocol       = "-1"
}
