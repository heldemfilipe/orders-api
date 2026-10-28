# O GitHub Actions prova quem e com um token assinado pelo GitHub; a AWS troca esse token por
# credenciais temporarias. Nao existe chave de acesso guardada em nenhum lugar.

resource "aws_iam_openid_connect_provider" "github" {
  count = var.criar_provedor_oidc ? 1 : 0

  url            = "https://token.actions.githubusercontent.com"
  client_id_list = ["sts.amazonaws.com"]
}

data "aws_iam_openid_connect_provider" "github" {
  count = var.criar_provedor_oidc ? 0 : 1

  url = "https://token.actions.githubusercontent.com"
}

locals {
  provedor_oidc_arn = var.criar_provedor_oidc ? aws_iam_openid_connect_provider.github[0].arn : data.aws_iam_openid_connect_provider.github[0].arn
}

data "aws_iam_policy_document" "github_assume" {
  statement {
    actions = ["sts:AssumeRoleWithWebIdentity"]

    principals {
      type        = "Federated"
      identifiers = [local.provedor_oidc_arn]
    }

    condition {
      test     = "StringEquals"
      variable = "token.actions.githubusercontent.com:aud"
      values   = ["sts.amazonaws.com"]
    }

    # so o workflow deste repositorio, rodando na branch configurada
    condition {
      test     = "StringEquals"
      variable = "token.actions.githubusercontent.com:sub"
      values   = ["repo:${var.repositorio_github}:ref:refs/heads/${var.branch_deploy}"]
    }
  }
}

resource "aws_iam_role" "deploy" {
  name               = "pedidos-github-deploy"
  assume_role_policy = data.aws_iam_policy_document.github_assume.json
}

data "aws_iam_policy_document" "deploy" {
  statement {
    sid       = "LoginNoEcr"
    actions   = ["ecr:GetAuthorizationToken"]
    resources = ["*"]
  }

  statement {
    sid = "EnviarImagem"
    actions = [
      "ecr:BatchCheckLayerAvailability",
      "ecr:BatchGetImage",
      "ecr:CompleteLayerUpload",
      "ecr:GetDownloadUrlForLayer",
      "ecr:InitiateLayerUpload",
      "ecr:PutImage",
      "ecr:UploadLayerPart",
    ]
    resources = [aws_ecr_repository.api.arn]
  }

  # estas duas acoes do ECS nao aceitam restricao por recurso
  statement {
    sid       = "RegistrarDefinicaoDeTarefa"
    actions   = ["ecs:DescribeTaskDefinition", "ecs:RegisterTaskDefinition"]
    resources = ["*"]
  }

  statement {
    sid       = "AtualizarOServico"
    actions   = ["ecs:DescribeServices", "ecs:UpdateService"]
    resources = [aws_ecs_service.api.id]
  }

  statement {
    sid       = "DescobrirAUrl"
    actions   = ["elasticloadbalancing:DescribeLoadBalancers"]
    resources = ["*"]
  }

  statement {
    sid       = "PassarOPapelDeExecucao"
    actions   = ["iam:PassRole"]
    resources = [aws_iam_role.execucao.arn]

    condition {
      test     = "StringEquals"
      variable = "iam:PassedToService"
      values   = ["ecs-tasks.amazonaws.com"]
    }
  }
}

resource "aws_iam_role_policy" "deploy" {
  name   = "deploy-ecs"
  role   = aws_iam_role.deploy.id
  policy = data.aws_iam_policy_document.deploy.json
}
