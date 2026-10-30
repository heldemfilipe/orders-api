#!/usr/bin/env bash
# Atualiza a tag da imagem da API no repositório de config (GitOps) e abre um pull request.
# O cluster não é tocado aqui: depois do merge, o Argo CD sincroniza sozinho.
#
# Variáveis:
#   IMAGE        imagem sem tag, ex.: ghcr.io/heldemfilipe/orders-api
#   IMAGE_TAG    tag nova, ex.: sha-<commit>
#   SOURCE_URL   link do commit que originou a imagem (vai no corpo do PR)
#   GH_TOKEN     token com acesso só ao repositório de config (contents e pull requests)
#   CONFIG_REPO  owner/nome do repositório de config (padrão: heldemfilipe/eks-gitops-config)
#   DRY_RUN=1    mostra o diff e para antes do push e do PR
set -euo pipefail

: "${IMAGE:?}" "${IMAGE_TAG:?}" "${SOURCE_URL:?}"
CONFIG_REPO="${CONFIG_REPO:-heldemfilipe/eks-gitops-config}"
ARQUIVO="workloads/pedidos/kustomization.yaml"
BRANCH="atualiza-imagem-${IMAGE_TAG#sha-}"
BRANCH="${BRANCH:0:40}"

trabalho="$(mktemp -d)"
trap 'rm -rf "$trabalho"' EXIT

clone_url="https://github.com/${CONFIG_REPO}.git"
if [ -n "${GH_TOKEN:-}" ]; then
  clone_url="https://x-access-token:${GH_TOKEN}@github.com/${CONFIG_REPO}.git"
fi
git clone --quiet --depth 1 "$clone_url" "$trabalho/config"
cd "$trabalho/config"

# Troca só o newTag da imagem pedida. Falha se a imagem não estiver no arquivo.
if ! grep -qxF "  - name: ${IMAGE}" "$ARQUIVO"; then
  echo "::error::imagem ${IMAGE} não encontrada em ${ARQUIVO}" >&2
  exit 1
fi
awk -v img="$IMAGE" -v tag="$IMAGE_TAG" '
  $1 == "-" && $2 == "name:" && $3 == img { achou = 1 }
  achou && $1 == "newTag:" { print "    newTag: " tag; achou = 0; next }
  { print }
' "$ARQUIVO" > "$ARQUIVO.novo"
mv "$ARQUIVO.novo" "$ARQUIVO"

if git diff --quiet; then
  echo "A tag ${IMAGE_TAG} já está no config. Nada a fazer."
  exit 0
fi
git --no-pager diff

if [ "${DRY_RUN:-}" = "1" ]; then
  echo "DRY_RUN=1: parando antes do push e do PR."
  exit 0
fi

git config user.name "orders-api CI"
git config user.email "ci@users.noreply.github.com"
git checkout --quiet -b "$BRANCH"
git commit --quiet -am "chore: imagem da API ${IMAGE_TAG}" -m "Origem: ${SOURCE_URL}"
git push --quiet --force origin "$BRANCH"

gh pr create --repo "$CONFIG_REPO" --base main --head "$BRANCH" \
  --title "chore: imagem da API ${IMAGE_TAG}" \
  --body "Atualiza a tag da imagem da API para \`${IMAGE_TAG}\`.

Origem: ${SOURCE_URL}

Depois do merge, o Argo CD sincroniza o cluster sozinho."
