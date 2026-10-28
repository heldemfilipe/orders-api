# orders-api

API REST de pedidos em Java com Spring Boot e MongoDB, construída como projeto de estudo em etapas: CRUD, testes e CI, Kafka, tratamento de falhas, circuit breaker e deploy.

[![CI](https://github.com/heldemfilipe/orders-api/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/heldemfilipe/orders-api/actions/workflows/ci.yml)

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
| Testes | JUnit 5, Mockito, AssertJ e Testcontainers |
| CI | GitHub Actions, com imagem publicada no GHCR |
| Container | Docker multi-stage (JRE Alpine, usuário não-root) |
| Mensageria | Apache Kafka 4.2 (KRaft, sem Zookeeper) com Spring Kafka |
| Inspeção do Kafka | Kafka UI (kafbat), porta 8085 |

## Como rodar

Requisitos: Docker (e JDK 25, só se for rodar sem container).

```bash
docker compose up -d --build
```

Sobe o MongoDB, o Kafka, o Kafka UI e a API.

- API: http://localhost:8080 (Swagger UI em http://localhost:8080/swagger-ui/index.html, saúde em `/actuator/health`)
- Kafka UI: http://localhost:8085
- Kafka para clientes que rodam no seu computador: `localhost:9094`

Para desenvolver com a API fora do container, suba só a infraestrutura e rode o Maven. A porta 8080 não pode estar ocupada pelo container da API: pare-o com `docker compose stop api` ou use outra porta (`SERVER_PORT=8081`).

```bash
docker compose up -d mongo kafka
./mvnw spring-boot:run
```

### Configuração

A aplicação lê `MONGO_USER`, `MONGO_PASSWORD`, `MONGO_HOST` e `KAFKA_BOOTSTRAP_SERVERS` do ambiente. Sem nada configurado, valem os padrões de desenvolvimento: `pedidos` / `pedidos` / `localhost` para o Mongo e `localhost:9094` para o Kafka. No Compose, a API recebe `mongo` e `kafka:9092`.

Para trocar as credenciais do Mongo, copie `.env.example` para `.env`. Quem lê esse arquivo é o Docker Compose, que usa os valores no MongoDB e os repassa à API como variáveis de ambiente. Rodando a API fora do container (`./mvnw spring-boot:run`), ela não lê o `.env`: exporte as variáveis no terminal.

### Por que o Kafka tem dois endereços

Um cliente Kafka conecta no endereço inicial, o broker responde com o endereço que **anuncia** (`advertised.listeners`) e o cliente reconecta nele. Esse endereço precisa ser alcançável por quem pergunta, então o broker anuncia dois:

| Quem conecta | Endereço |
| --- | --- |
| Containers da rede do Compose (a API no Compose, o Kafka UI) | `kafka:9092` |
| O seu computador (API via `./mvnw`, ferramentas de linha de comando) | `localhost:9094` |

### Parar

```bash
docker compose down
```

Os dados ficam nos volumes `mongo-data` e `kafka-data` e sobrevivem. Para apagá-los também: `docker compose down -v`.

## A API

| Método | Rota | O que faz | Sucesso |
| --- | --- | --- | --- |
| `POST` | `/pedidos` | Cria um pedido | `201` com `Location` |
| `GET` | `/pedidos/{id}` | Busca um pedido | `200` |
| `GET` | `/pedidos?page=0&size=20` | Lista paginada, mais recentes primeiro | `200` |
| `PUT` | `/pedidos/{id}` | Substitui itens e status | `200` |
| `DELETE` | `/pedidos/{id}` | Remove um pedido | `204` |
| `POST` | `/pedidos/eventos` | Valida e publica o evento `PedidoCriado` no Kafka | `202` com `pedidoId` e `eventId` |

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

### Publicar um pedido como evento

`POST /pedidos/eventos` recebe o mesmo corpo do `POST /pedidos`, mas **não grava no banco**: valida, monta o evento `PedidoCriado` e o publica no tópico `pedidos.criados` (3 partições, chave `clienteId`).

```bash
curl -X POST http://localhost:8080/pedidos/eventos \
  -H "Content-Type: application/json" \
  -d '{"clienteId":"cli-1","itens":[{"produto":"caneta","quantidade":2,"precoUnitario":3.50}]}'
```

```json
{ "pedidoId": "41c361c7-22c3-439b-94ae-4f96cf926a15", "eventId": "670341b2-fc2a-43e6-8e51-7ab6a002534d" }
```

O `pedidoId` é o id que o pedido terá quando for gravado. Para ver a mensagem no tópico, com chave e partição:

```bash
docker exec pedidos-kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server kafka:9092 --topic pedidos.criados --from-beginning --formatter-property print.key=true --formatter-property print.partition=true
```

Mensagens do mesmo `clienteId` aparecem sempre na mesma partição. Quem grava o pedido no banco a partir desse evento é o consumer A (veja "Consumers e consumer groups"): depois do `202`, o pedido fica disponível em `GET /pedidos/{pedidoId}` assim que ele o grava.

### Erros

Todos os erros usam `application/problem+json` (RFC 9457).

| Código | Quando | Exemplo de `title` |
| --- | --- | --- |
| `400` | Validação ou JSON inválido | `Erro de validação`, com `erros` por campo |
| `404` | Pedido não existe | `Pedido não encontrado` |
| `409` | Mudança de status não permitida | `Transição de status inválida` |
| `503` | Kafka indisponível ao publicar | `Fila indisponível` |

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

## Consumers e consumer groups

O tópico `pedidos.criados` é lido por dois consumers, cada um no seu group:

| Consumer | Group | O que faz |
| --- | --- | --- |
| A, `PedidoCriadoPersistenciaListener` | `persistencia` | Grava o `Pedido` no MongoDB, usando o `pedidoId` do evento como `_id` |
| B, `PedidoCriadoAuditoriaListener` | `auditoria` | Grava em `auditoria_eventos` o `eventId`, a partição, o offset, o horário e a instância que processou |

Dentro de um group, cada partição é lida por **um** membro por vez. Groups diferentes são independentes: cada um recebe **todas** as mensagens. Com `auto-offset-reset: earliest`, um group novo começa do início do tópico, em vez de ignorar o que já foi publicado.

Cada instância da aplicação roda os dois listeners, então duas instâncias formam dois membros em cada group.

### Experimentos

Cada experimento foi montado com instâncias da aplicação em portas diferentes, todas no mesmo Mongo e no mesmo Kafka. A coluna "observado" é o que de fato aconteceu na minha máquina.

| # | Experimento | Como montar | Resultado esperado | Resultado observado | Por quê |
| --- | --- | --- | --- | --- | --- |
| 1 | Mesmo group, 2 instâncias | 2 instâncias (8091 e 8092), 9 pedidos de clientes `exp1-*` | As 3 partições se dividem entre elas e cada mensagem é processada por uma só | O `describe` mostrou as partições 0 e 1 com um membro (`#PARTITIONS` 2) e a partição 2 com o outro (1), lag 0. As 9 mensagens foram processadas **só pela 8092** (6 na partição 0, 3 na partição 1) e **nenhuma pela 8091**. 9 pedidos, 9 auditorias, 0 duplicatas. | Cada partição pertence a um único membro do group (nos logs aparece `protocol='range'`), então nenhuma mensagem é processada duas vezes. A divisão é de **partições**, e não de mensagens: a chave decide a partição, e os 9 clientes caíram só nas partições 0 e 1. Quem tinha a partição 2 ficou sem trabalho. O paralelismo real depende de como as chaves se distribuem, e não só do número de instâncias. |
| 2 | Groups diferentes | Olhar `persistencia` e `auditoria` sobre as mesmas mensagens | Os dois recebem todas as mensagens | Para o prefixo `exp1-`: 9 pedidos gravados pelo A e 9 registros de auditoria do B, sem duplicatas. O group `auditoria`, criado depois, também leu desde o início um evento antigo (`cli-1`, partição 0, offset 0) que o `persistencia` já tinha consumido antes. | Cada group guarda o **seu próprio offset** por partição, e ler não consome a mensagem: o Kafka mantém o log e cada group avança no seu ritmo. Isso permite ligar um consumer novo para outro fim (auditoria, relatório) sem mexer nos que já existem, e o `earliest` faz o group novo começar do início. |
| 3 | Mais instâncias que partições | 4 instâncias no mesmo group, com 3 partições | Uma instância fica sem partição e ociosa | Três instâncias receberam uma partição cada: nos logs, a 8092 registrou `partitions assigned: [pedidos.criados-0]` e a 8093, `[pedidos.criados-1]`, e a auditoria mostra a partição 2 com a 8091. A 8094 registrou `partitions assigned: []` nos dois groups. Mensagens por instância na auditoria: 8091 → 4, 8092 → 2, 8093 → 3, 8094 → 0. 9 pedidos, 9 auditorias. | Uma partição só pode ter um consumidor por group ao mesmo tempo, então com 3 partições o paralelismo máximo é 3. O quarto membro não acelera nada, mas fica de **reserva**, pronto para assumir. O experimento 4 mostra isso. |
| 4 | Rebalanceamento | Derrubar uma instância com mensagens fluindo (desligamento limpo e à força) | As partições dela migram para as demais, visível nos logs | Com tráfego a cada 2 s, parei a 8093 com `Ctrl+C` (desligamento limpo). Os logs mostram `Consumer stopped` e `Graceful shutdown complete` às 12:00:55,6, e a 8094, que estava ociosa, recebeu a partição 1 (`partitions assigned: [pedidos.criados-1]`) às 12:00:55,4. A primeira mensagem seguinte dessa partição (`exp4-21`) foi consumida pela 8094 sem atraso. No total, 34 pedidos e 34 auditorias para `exp4-*`, 0 duplicatas, e o maior intervalo entre criar o evento e consumi-lo foi de 0,5 s. Na rodada à força, derrubei duas instâncias com `Stop-Process -Force`, **sem tráfego fluindo** (nenhuma mensagem foi consumida depois das 12:03, offsets finais 30, 30 e 17, lag 0). Os logs das instâncias terminam de repente, sem `Consumer stopped` nem `Graceful shutdown complete`. O log do broker mostra que elas **não** pediram para sair: foram removidas por `heartbeat expiration` (`has failed, removing it from the group`), a primeira às 12:09:06 (a que tinha as partições 0 e 1, e o group passou da geração 21 para a 22) e a segunda às 12:09:39. No desligamento limpo, o broker registrou `explicit LeaveGroup request` (por exemplo, às 12:02:36). Não anotei a hora exata dos `Stop-Process`, então o intervalo entre a morte e a remoção não está medido aqui, e como não havia tráfego também não há mensagem atrasada para mostrar. | No desligamento limpo a instância avisa o Kafka que está saindo, e o group se reorganiza na hora: a partição foi para a reserva que já estava no group, sem perda e sem reprocessamento. Quando um consumer cai **sem avisar**, o Kafka só o considera morto depois de um tempo sem sinal de vida (timeout de sessão), e nesse intervalo a partição dele fica parada. O timeout de sessão padrão do cliente é de 45 s. Numa validação separada, com tráfego fluindo, a mesma configuração e um Kafka de teste, uma partição ficou cerca de 45 s sem consumo até ser reatribuída. |
| 5 | Ordem | 6 mensagens seguidas do mesmo `clienteId` (`exp5-ordem`) | Mesma partição, processadas na ordem de envio | Os 6 eventos foram para a partição 1, com offsets 24 a 29 consecutivos, todos pela 8092. Os logs mostram `persistencia` e `auditoria` processando em sequência, e os pedidos, ordenados por `criadoEm`, saem `seq-1` a `seq-6`. | A chave determina a partição (hash da chave), e uma partição é um log ordenado, lido por um único consumidor, um registro de cada vez. A ordem vale **dentro de uma partição**, ou seja, por cliente, e não entre clientes diferentes. Deixaria de valer se o número de partições mudasse (a mesma chave iria para outra partição) ou se o consumer processasse registros em paralelo. |

O que mais surpreendeu: com 9 clientes, nenhuma mensagem foi para a partição 2 (experimento 1). Ter mais instâncias não garante trabalho para todas, porque quem decide é a chave.

### Como reproduzir

```bash
./mvnw -DskipTests clean package
java -jar target/orders-api-0.0.1-SNAPSHOT.jar --server.port=8091
```

Uma instância por terminal, mudando só a porta. A API do Compose precisa estar parada (`docker compose stop api`), senão ela entra nos groups. Use um prefixo de `clienteId` novo em cada experimento.

Estado dos groups (partições, offsets, lag e dono de cada partição) e dos membros (a coluna `#PARTITIONS` mostra quem está ocioso):

```bash
docker exec pedidos-kafka /opt/kafka/bin/kafka-consumer-groups.sh --bootstrap-server kafka:9092 --describe --group persistencia
docker exec pedidos-kafka /opt/kafka/bin/kafka-consumer-groups.sh --bootstrap-server kafka:9092 --describe --group persistencia --members --verbose
```

Quem processou o quê, pela auditoria (troque o prefixo do cliente):

```bash
docker exec pedidos-mongo mongosh -u pedidos -p pedidos --authenticationDatabase admin pedidos --quiet --eval "db.auditoria_eventos.find({clienteId: /^exp1-/}, {_id: 0, clienteId: 1, particao: 1, offset: 1, instancia: 1}).sort({particao: 1, offset: 1}).forEach(d => print(d.clienteId, d.particao, Number(d.offset), d.instancia))"
```

Nos logs de cada instância, as linhas `persistencia: partitions assigned: [...]` e `partitions revoked: [...]` mostram quais partições ela recebeu e perdeu.

## Testes

```bash
./mvnw verify
```

Os testes de integração usam Testcontainers e sobem um MongoDB e um Kafka descartáveis (`@ServiceConnection`), então o Docker precisa estar rodando. Eles não usam o Mongo nem o Kafka do Compose. Os testes de unidade e os testes da camada web não precisam de Docker.

| Teste | O que prova |
| --- | --- |
| `StatusPedidoTest`, `PedidoServiceTest` | Transições de status e cálculo do total |
| `PedidoIntegracaoTest` | CRUD de ponta a ponta contra um MongoDB real |
| `PedidoEventoIntegracaoTest` | O evento chega ao tópico, com a chave do cliente, sem cabeçalhos de tipo, e o mesmo cliente cai na mesma partição |
| `PedidoEventoServiceTest`, `PedidoEventoPublisherTest` | Montagem do evento e conversão das falhas do Kafka em `EventoNaoPublicadoException` |
| `PedidoEventoControllerTest` | `202`, `400` sem publicar e `503` quando a fila está fora |

## Estrutura

```
src/main/java/com/exemplo/pedidos
├── PedidosApplication.java
├── pedido
│   ├── Pedido.java, ItemPedido.java, StatusPedido.java   documento e regras de status
│   ├── PedidoRepository.java                            acesso ao MongoDB
│   ├── PedidoService.java                               total e transições
│   ├── PedidoController.java                            HTTP do CRUD
│   ├── PedidoEventoController.java, PedidoEventoService.java   POST /pedidos/eventos
│   └── dto                                              contrato da API (records)
├── kafka
│   ├── PedidoCriado.java                                contrato do evento (record)
│   ├── PedidoEventoPublisher.java                       única classe que fala com o KafkaTemplate
│   ├── PedidoCriadoPersistenciaListener.java            consumer A (group persistencia)
│   ├── PedidoCriadoAuditoriaListener.java               consumer B (group auditoria)
│   └── KafkaTopicConfig.java                            declara o tópico pedidos.criados
├── auditoria
│   ├── AuditoriaEvento.java                             documento da coleção auditoria_eventos (record)
│   └── AuditoriaEventoRepository.java
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
- **`POST /pedidos/eventos` espera a confirmação do Kafka.** O `202` significa que o broker recebeu o evento, e não que o pedido já foi gravado no banco (isso é feito pelo consumer). Se o Kafka estiver fora, a API responde `503` em poucos segundos e o evento não é entregue depois: o producer tem `max.block.ms`, `request.timeout.ms` e `delivery.timeout.ms` curtos para a falha ser definitiva. Repetir a chamada gera um evento novo, com outro `eventId`. A alternativa para não depender do Kafka na hora do `POST` é o transactional outbox.
- **Chave da mensagem = `clienteId`.** Mensagens do mesmo cliente caem na mesma partição e mantêm a ordem entre si. O tópico tem 3 partições para a Etapa 4 ter o que mostrar nos consumer groups.
- **`acks=all`.** O líder só confirma depois de todas as réplicas sincronizadas. Com um broker só não muda nada na prática, mas é o hábito que vale em produção.
- **JSON sem cabeçalho de tipo.** O serializador não grava o nome da classe Java na mensagem, então o contrato é o JSON, e cada consumer escolhe em que tipo convertê-lo.
- **Dois groups, um por finalidade.** `persistencia` e `auditoria` leem o mesmo tópico de forma independente. Acrescentar um terceiro consumer (um relatório, por exemplo) não exige mexer nos dois primeiros.
- **O consumer A usa o `pedidoId` do evento como `_id`.** O `save` do Spring Data substitui o documento de mesmo id, então ler o mesmo evento duas vezes não cria dois pedidos. A garantia completa de idempotência fica para a Etapa 5.
- **O `total` não é recalculado no consumer.** Ele confia no contrato: quem calcula é o servidor, ao montar o evento.
- **A auditoria guarda a `instancia`.** É o campo que permite provar, depois do fato, qual instância processou qual partição. Sem ele, os experimentos só se veriam nos logs.
- **Kafka isolado em uma classe.** Só o `PedidoEventoPublisher` conhece o `KafkaTemplate`, o que facilita testar as falhas com um dublê.
- **Dois listeners no Kafka** (`kafka:9092` e `localhost:9094`), explicados em "Como rodar".
- **Credenciais padrão `pedidos`/`pedidos`.** Só para desenvolvimento local. Em qualquer ambiente real, vêm de variáveis de ambiente ou de um gerenciador de segredos.

## Roteiro

- [x] Etapa 1: CRUD com MongoDB, validação e erros padronizados
- [x] Etapa 2: testes com Testcontainers e CI no GitHub Actions
- [x] Etapa 3: Kafka, producer
- [ ] Etapa 4: consumers e consumer groups
- [ ] Etapa 5: retry, dead-letter e idempotência
- [ ] Etapa 6: circuit breaker com Resilience4j
- [ ] Etapa 7: deploy do CRUD na nuvem

## Licença

MIT. Veja [LICENSE](LICENSE).
