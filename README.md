# orders-api

API REST de pedidos em Java com Spring Boot e MongoDB, construída como projeto de estudo em etapas: CRUD, testes e CI, Kafka, tratamento de falhas, circuit breaker e deploy.

<!-- O badge do CI entra aqui na Etapa 2 -->

## O problema

Uma loja precisa registrar pedidos: quem comprou, o quê, quanto custa e em que ponto o pedido está (criado, pago ou cancelado). A API expõe esse cadastro e protege três regras:

- o cliente nunca informa o total, o servidor o calcula;
- dado inválido é recusado na entrada, com a lista dos campos errados;
- um pedido só muda de status por caminhos permitidos.

## Tecnologias

| Peça | Escolha |
| --- | --- |
| Linguagem | Java 25 (LTS) |
| Framework | Spring Boot 4.1 |
| Banco | MongoDB 8 |
| Build | Maven com wrapper (`./mvnw`) |
| Documentação da API | springdoc-openapi (Swagger UI) |
| Execução local | Docker Compose |

## Como rodar

Requisitos: JDK 25 e Docker.

```bash
docker compose up -d
./mvnw spring-boot:run
```

O Compose sobe o MongoDB; a API roda na sua máquina, na porta 8080. A API dentro do Compose chega na Etapa 2.

- Swagger UI: http://localhost:8080/swagger-ui/index.html
- Saúde: http://localhost:8080/actuator/health

No Windows, use `mvnw.cmd` no lugar de `./mvnw` se o terminal não reconhecer o script.

### Configuração

A aplicação lê `MONGO_USER`, `MONGO_PASSWORD` e `MONGO_HOST` do ambiente. Sem nada configurado, valem os padrões de desenvolvimento (`pedidos` / `pedidos` / `localhost`). Veja `.env.example`.

### Parar

```bash
docker compose down
```

Os dados ficam no volume `mongo-data` e sobrevivem. Para apagá-los também: `docker compose down -v`.

## A API

| Método | Rota | O que faz | Sucesso |
| --- | --- | --- | --- |
| `POST` | `/pedidos` | Cria um pedido | `201` com `Location` |
| `GET` | `/pedidos/{id}` | Busca um pedido | `200` |
| `GET` | `/pedidos?page=0&size=20` | Lista paginada, mais recentes primeiro | `200` |
| `PUT` | `/pedidos/{id}` | Substitui itens e status | `200` |
| `DELETE` | `/pedidos/{id}` | Remove um pedido | `204` |

### Exemplo

```bash
curl -X POST http://localhost:8080/pedidos \
  -H "Content-Type: application/json" \
  -d '{"clienteId":"cli-1","itens":[{"produto":"caneta","quantidade":2,"precoUnitario":3.50}]}'
```

```json
{
  "id": "6adbe0f1fd75f8150a2a93f8",
  "clienteId": "cli-1",
  "itens": [{ "produto": "caneta", "quantidade": 2, "precoUnitario": 3.50 }],
  "total": 7.00,
  "status": "CRIADO",
  "criadoEm": "2026-10-23T22:34:25.059Z"
}
```

O `total` é calculado pelo servidor; `id`, `status` e `criadoEm` também.

### Erros

Todos os erros usam `application/problem+json` (RFC 9457).

| Código | Quando | Exemplo de `title` |
| --- | --- | --- |
| `400` | Validação ou JSON inválido | `Erro de validação`, com `erros` por campo |
| `404` | Pedido não existe | `Pedido não encontrado` |
| `409` | Mudança de status não permitida | `Transição de status inválida` |

```json
{
  "title": "Erro de validação",
  "status": 400,
  "detail": "Dados inválidos.",
  "instance": "/pedidos",
  "erros": { "itens[0].produto": "não deve estar em branco" }
}
```

### Transições de status

| De \ Para | `CRIADO` | `PAGO` | `CANCELADO` |
| --- | --- | --- | --- |
| `CRIADO` | sim | sim | sim |
| `PAGO` | não | não | sim |
| `CANCELADO` | não | não | não |

Um pedido `CRIADO` pode ser editado. Um `PAGO` só pode ser cancelado. Um `CANCELADO` não muda mais.

## Estrutura

```
src/main/java/com/exemplo/pedidos
├── PedidosApplication.java
├── pedido
│   ├── Pedido.java, ItemPedido.java, StatusPedido.java   documento e regras de status
│   ├── PedidoRepository.java                            acesso ao MongoDB
│   ├── PedidoService.java                               total e transições
│   ├── PedidoController.java                            HTTP
│   └── dto                                              contrato da API (records)
└── erro
    ├── GlobalExceptionHandler.java                      erros viram ProblemDetail
    └── *Exception.java
```

## Decisões e trade-offs

- **MongoDB.** O pedido é lido e gravado inteiro, com os itens dentro. Um documento só evita junções. O custo é perder integridade relacional entre pedido e cliente.
- **Total calculado no servidor.** O DTO de entrada não tem o campo `total`, então o cliente não pode mentir sobre o valor.
- **`BigDecimal` para dinheiro.** Gravado como `Decimal128` no Mongo, que permite ordenar e comparar valores com exatidão. `double` acumularia erros de arredondamento.
- **Entidade e DTO separados.** Mudar o banco não muda o contrato da API.
- **`409` para transição inválida.** O JSON está correto, o que conflita é o estado atual do pedido. `400` fica para requisição malformada.
- **Regra de transição no enum.** Fica junto do domínio e o compilador obriga a tratar todo status novo.
- **Credenciais padrão `pedidos`/`pedidos`.** Só para desenvolvimento local. Em qualquer ambiente real, vêm de variáveis de ambiente ou de um gerenciador de segredos.

## Roteiro

- [x] Etapa 1: CRUD com MongoDB, validação e erros padronizados
- [ ] Etapa 2: testes com Testcontainers e CI no GitHub Actions
- [ ] Etapa 3: Kafka, producer
- [ ] Etapa 4: consumers e consumer groups
- [ ] Etapa 5: retry, dead-letter e idempotência
- [ ] Etapa 6: circuit breaker com Resilience4j
- [ ] Etapa 7: deploy do CRUD na nuvem

## Licença

MIT. Veja [LICENSE](LICENSE).
