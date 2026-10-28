resource "aws_lb" "api" {
  name               = "pedidos-alb"
  load_balancer_type = "application"
  security_groups    = [aws_security_group.alb.id]
  subnets            = aws_subnet.publica[*].id
}

resource "aws_lb_target_group" "api" {
  name        = "pedidos-api"
  port        = 8080
  protocol    = "HTTP"
  target_type = "ip" # Fargate registra o IP da tarefa
  vpc_id      = aws_vpc.principal.id

  deregistration_delay = 30

  health_check {
    path                = "/actuator/health"
    matcher             = "200"
    interval            = 15
    timeout             = 5
    healthy_threshold   = 2
    unhealthy_threshold = 3
  }
}

# HTTP puro: sem dominio proprio nao ha certificado ACM. Em producao seria 443 com redirect do 80.
resource "aws_lb_listener" "http" {
  load_balancer_arn = aws_lb.api.arn
  port              = 80
  protocol          = "HTTP"

  default_action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.api.arn
  }
}
