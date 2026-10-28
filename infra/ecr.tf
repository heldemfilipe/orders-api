resource "aws_ecr_repository" "api" {
  name         = "pedidos-api"
  force_delete = true # o destroy apaga as imagens junto; e o que se quer num ambiente de estudo

  image_scanning_configuration {
    scan_on_push = true
  }
}

# o pipeline envia uma tag por commit; sem isso o repositorio so cresce
resource "aws_ecr_lifecycle_policy" "api" {
  repository = aws_ecr_repository.api.name

  policy = jsonencode({
    rules = [{
      rulePriority = 1
      description  = "Mantem as 5 imagens mais recentes"
      selection = {
        tagStatus   = "any"
        countType   = "imageCountMoreThan"
        countNumber = 5
      }
      action = { type = "expire" }
    }]
  })
}
