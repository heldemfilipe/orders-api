resource "aws_ecs_cluster" "principal" {
  name = "pedidos"
}

resource "aws_cloudwatch_log_group" "api" {
  name              = "/ecs/pedidos-api"
  retention_in_days = 7
}

# o papel que o ECS usa para PREPARAR a tarefa: baixar a imagem, gravar logs e ler o segredo.
# A aplicacao em si nao recebe papel nenhum (nao fala com nenhum servico da AWS).
data "aws_iam_policy_document" "ecs_assume" {
  statement {
    actions = ["sts:AssumeRole"]

    principals {
      type        = "Service"
      identifiers = ["ecs-tasks.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "execucao" {
  name               = "pedidos-ecs-execucao"
  assume_role_policy = data.aws_iam_policy_document.ecs_assume.json
}

resource "aws_iam_role_policy_attachment" "execucao_padrao" {
  role       = aws_iam_role.execucao.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy"
}

data "aws_iam_policy_document" "ler_segredo" {
  statement {
    actions   = ["secretsmanager:GetSecretValue"]
    resources = [aws_secretsmanager_secret.mongodb_uri.arn]
  }
}

resource "aws_iam_role_policy" "execucao_segredo" {
  name   = "ler-mongodb-uri"
  role   = aws_iam_role.execucao.id
  policy = data.aws_iam_policy_document.ler_segredo.json
}

# A definicao inicial so existe para o servico poder ser criado. A cada deploy o pipeline registra
# uma revisao nova com a imagem do commit (por isso o service ignora mudancas em task_definition).
resource "aws_ecs_task_definition" "api" {
  family                   = "pedidos-api"
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = var.cpu
  memory                   = var.memoria
  execution_role_arn       = aws_iam_role.execucao.arn

  container_definitions = jsonencode([{
    name         = "api"
    image        = "${aws_ecr_repository.api.repository_url}:inicial"
    essential    = true
    portMappings = [{ containerPort = 8080, protocol = "tcp" }]

    environment = [
      { name = "SPRING_PROFILES_ACTIVE", value = "cloud" },
    ]

    # injetado em tempo de execucao pelo ECS; nao aparece na imagem nem no repositorio
    secrets = [
      { name = "MONGODB_URI", valueFrom = aws_secretsmanager_secret.mongodb_uri.arn },
    ]

    logConfiguration = {
      logDriver = "awslogs"
      options = {
        "awslogs-group"         = aws_cloudwatch_log_group.api.name
        "awslogs-region"        = var.regiao
        "awslogs-stream-prefix" = "api"
      }
    }
  }])
}

resource "aws_ecs_service" "api" {
  name            = "pedidos-api"
  cluster         = aws_ecs_cluster.principal.id
  task_definition = aws_ecs_task_definition.api.arn
  desired_count   = var.quantidade_tarefas
  launch_type     = "FARGATE"

  # a JVM leva alguns segundos para subir; sem a carencia o ALB reprovaria a tarefa antes da hora
  health_check_grace_period_seconds = 120

  network_configuration {
    subnets          = aws_subnet.publica[*].id
    security_groups  = [aws_security_group.tarefa.id]
    assign_public_ip = true
  }

  load_balancer {
    target_group_arn = aws_lb_target_group.api.arn
    container_name   = "api"
    container_port   = 8080
  }

  depends_on = [aws_lb_listener.http]

  lifecycle {
    ignore_changes = [task_definition]
  }
}
