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
| Banco | MongoDB 8 no Compose (7.0 nos testes, veja "Decisões e trade-offs") |
| Build | Maven com wrapper (`./mvnw`) |
| Documentação da API | springdoc-openapi (Swagger UI) |
| Execução local | Docker Compose |
| Testes | JUnit 5, Mockito, AssertJ e Testcontainers |
| CI | GitHub Actions, com imagem publicada no GHCR |
| Container | Docker multi-stage (JRE Alpine, usuário não-root) |
| Mensageria | Apache Kafka 4.2 (KRaft, sem Zookeeper) com Spring Kafka |
| Inspeção do Kafka | Kafka UI (kafbat), porta 8085 |
| Circuit breaker | Resilience4j 2.4 (`resilience4j-spring-boot4`) |
| Serviço de frete simulado | WireMock 3.13, porta 8089 |
| Arquitetura | Hexagonal (portas e adaptadores), com as fronteiras verificadas pelo ArchUnit |
| Nuvem | AWS: ECS Fargate, load balancer, ECR e Secrets Manager, declarados em Terraform (`infra/`) |
| Banco na nuvem | MongoDB Atlas (cluster gratuito) |
| Deploy | GitHub Actions manual (`workflow_dispatch`), autenticado na AWS por OIDC |

## Como rodar

Requisitos: Docker (e JDK 25, só se for rodar sem container).

```bash
docker compose up -d --build
```

Sobe o MongoDB, o Kafka, o Kafka UI, o WireMock (serviço de frete simulado) e a API.

- API: http://localhost:8080 (Swagger UI em http://localhost:8080/swagger-ui/index.html, saúde em `/actuator/health`)
- Kafka UI: http://localhost:8085
- Kafka para clientes que rodam no seu computador: `localhost:9094`
- Frete simulado (WireMock): http://localhost:8089/frete?clienteId=cli-1

Para desenvolver com a API fora do container, suba só a infraestrutura e rode o Maven. A porta 8080 não pode estar ocupada pelo container da API: pare-o com `docker compose stop api` ou use outra porta (`SERVER_PORT=8081`).

```bash
docker compose up -d mongo kafka frete
./mvnw spring-boot:run
```

### Configuração

A aplicação lê `MONGO_USER`, `MONGO_PASSWORD`, `MONGO_HOST`, `KAFKA_BOOTSTRAP_SERVERS` e `FRETE_URL` do ambiente. Sem nada configurado, valem os padrões de desenvolvimento: `pedidos` / `pedidos` / `localhost` para o Mongo, `localhost:9094` para o Kafka e `http://localhost:8089` para o frete. No Compose, a API recebe `mongo`, `kafka:9092` e `http://frete:8080`.

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
  "criadoEm": "2026-10-23T22:34:25.059Z",
  "frete": null,
  "freteEstimado": false
}
```

O `total` é calculado pelo servidor; `id`, `status` e `criadoEm` também. O `frete` e o `freteEstimado` só são preenchidos pelo fluxo de eventos (veja "Circuit breaker no frete" abaixo), e por isso aqui vêm vazios.

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
| A, `PedidoCriadoPersistenciaListener` | `persistencia` | Consulta o frete (protegido por circuit breaker) e grava o `Pedido` no MongoDB, usando o `pedidoId` do evento como `_id` |
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

## Falhas, retry e dead-letter

O que acontece quando o consumer não consegue processar uma mensagem. Há dois tipos de falha, e cada um é tratado em um ponto diferente:

| Falha | Onde acontece | O que o sistema faz |
| --- | --- | --- |
| **De negócio**: o listener lança uma exceção (hoje, `EventoInvalidoException` para `total` negativo) | Dentro do listener | `DefaultErrorHandler` tenta de novo 3 vezes, esperando 1 s, 2 s e 4 s. Se continuar falhando, o `DeadLetterPublishingRecoverer` publica a mensagem em `pedidos.criados.DLT` e o offset avança |
| **Mensagem ilegível**: o JSON está quebrado (*poison pill*) | No `poll()`, antes de o listener ser chamado | O `ErrorHandlingDeserializer` guarda o erro em vez de lançá-lo, e a mensagem segue o mesmo caminho até a DLT, com os **bytes originais** |

Quem lê a DLT é o `DeadLetterListener` (group `dlt`), que grava cada mensagem em `mensagens_falhas` com o tópico, a partição e o offset de origem, o group que falhou, a chave, o texto original, a exceção e o motivo. A DLT só **registra** a falha: não há reprocessamento automático.

### Experimentos

Medições feitas numa cópia isolada do projeto (Mongo e Kafka próprios), com o mesmo código que está no repositório.

| # | Experimento | Resultado observado | Por quê |
| --- | --- | --- | --- |
| 1 | Evento com `total` negativo (`experimentos/evento-negativo.txt`) | O documento apareceu em `mensagens_falhas` cerca de **8 s** depois da publicação, com `excecao` = `EventoInvalidoException`, `motivo` = `total negativo: -5` e `grupoOriginal` = `persistencia`. O pedido **não** foi gravado. | A espera soma 1 s + 2 s + 4 s = 7 s de tentativas, mais o envio à DLT. Repetir não adianta para um erro que é do dado, mas o custo é pequeno e cobre falhas passageiras (um Mongo que oscila). O consumer inteiro, com todas as partições, fica parado durante essas esperas (veja o experimento 1 de "Circuit breaker no frete"). |
| 2 | Listener da DLT falhando, **sem** proteção | O offset da partição 0 da DLT foi de 0 para 3 e para 6 em 60 s, **sem ninguém publicar nada**. | O listener da DLT também passa pelo `DefaultErrorHandler`. Quando ele falha, o recoverer publica a falha de volta na DLT, o listener lê de novo, falha de novo, e assim por diante. |
| 3 | O mesmo, **com** a guarda (o destino é `null` para registros que já estão na DLT) | Os offsets da DLT ficaram estáveis e o group `dlt` terminou com lag 0. | Quando o resolvedor devolve `null`, o recoverer não publica. O registro dessa falha se perde, o que é uma escolha consciente: melhor perder o registro de uma falha do que travar o sistema num loop. |
| 4 | Mensagem malformada (`experimentos/evento-malformado.txt`), **sem** `ErrorHandlingDeserializer` | O log cresceu **1,5 GB em cerca de 3 min 30 s**, a partir de **uma única** mensagem. No início, **152 linhas de erro por segundo**, sempre a mesma `RecordDeserializationException`. O processo gastou **37,6 s de CPU em 38 s** (um núcleo a 100%). Eventos válidos das partições 0 e 1 foram processados, e a partição do veneno ficou com **lag 2**, parada. | O deserializador roda dentro do `poll()`, antes do listener. O erro não chega ao `DefaultErrorHandler` do jeito esperado, o offset não avança e o `poll()` lê a mesma mensagem de novo, sem espera. O estrago fica na partição da mensagem, mas é total nela: tudo que vem depois também fica sem ser lido. |
| 5 | A mesma mensagem **com** `ErrorHandlingDeserializer` | A mensagem foi para a DLT sem passar pelas tentativas e o consumer seguiu. No `mensagens_falhas`, o texto original aparece **intacto**, uma vez por group que falhou (`persistencia` e `auditoria`). | O deserializador entrega o erro como dado, e não como exceção, então o fluxo normal da DLT vale. Os bytes originais são preservados por um segundo template, de `byte[]`: o template JSON reserializaria os bytes como uma string JSON (em base64), e o texto original se perderia. |
| 6 | Reentrega do mesmo evento depois de o pedido ser pago, com `save` | O pedido voltou de `PAGO` (total 6, quantidade 3) para `CRIADO` (total 2, quantidade 1). Continuava **1 documento**. | O `save` substitui o documento de mesmo `_id`. Impede duplicar, mas desfaz o que mudou depois. Por isso o teste da Etapa 4 (um pedido para dois eventos) passava e escondia o problema. |
| 7 | O mesmo, com `insert` e `DuplicateKeyException` | O pedido continuou `PAGO` (total 6, quantidade 3). O log registrou `ja existe (evento ... reentregue), ignorando`, e a auditoria registrou as duas entregas. | O `insert` recusa um `_id` que já existe, e o consumer trata a recusa como "já processei". A auditoria é um registro do que chegou, e não um efeito idempotente. |
| 8 | Queda do consumer depois de gravar e antes de confirmar (pausa temporária de 10 s após o `insert`, processo morto à força) | Com o consumer pausado, o pedido já estava no Mongo e o **lag era 1**. Depois da queda: 1 documento, lag 1. Ao reiniciar: `ja existe ... reentregue, ignorando`, **1 documento** e lag 0. | O Kafka só considera a mensagem processada quando o listener termina e o offset é confirmado. Se o processo morre no meio, a mensagem volta (entrega *pelo menos uma vez*). Como o `insert` é idempotente, a volta é inofensiva. |

### Como reproduzir

Os eventos dos experimentos estão em `experimentos/`: um arquivo por evento, no formato `chave#json`. Publicar um deles:

```bash
docker cp experimentos/evento-negativo.txt pedidos-kafka:/tmp/evento-negativo.txt
docker exec pedidos-kafka sh -c "/opt/kafka/bin/kafka-console-producer.sh --bootstrap-server kafka:9092 --topic pedidos.criados --reader-property parse.key=true --reader-property key.separator=# < /tmp/evento-negativo.txt"
```

O que a DLT registrou:

```bash
docker exec pedidos-mongo mongosh -u pedidos -p pedidos --authenticationDatabase admin pedidos --quiet --eval "db.mensagens_falhas.find({}, {_id: 0}).forEach(d => printjson(d))"
```

Cuidado com `evento-malformado.txt` **sem** o `ErrorHandlingDeserializer`: o laço de erros enche o disco em minutos. Para repetir o experimento 4, redirecione o log da aplicação para um arquivo e pare em alguns segundos.

## Circuit breaker no frete

Antes de gravar o pedido, o consumer A consulta um serviço de frete (`GET /frete?clienteId=...`, simulado por um WireMock no Compose). A chamada usa `RestClient` com timeout de conexão de 1 s e de leitura de 3 s, e é protegida por `@CircuitBreaker` do Resilience4j. Se o serviço falha, está lento ou o circuito está aberto, o fallback devolve um **frete padrão** (R$ 20,00, 7 dias), e o pedido é gravado com `freteEstimado: true`. O evento não vai para retry nem para a DLT por causa do frete.

Os estados do circuito:

| Estado | O que acontece |
| --- | --- |
| `CLOSED` | As chamadas passam, e o circuito conta falhas e chamadas lentas nas últimas 10 |
| `OPEN` | Nenhuma chamada chega ao serviço: todas vão direto para o fallback (`NOT_PERMITTED`) |
| `HALF_OPEN` | Passam 3 chamadas de teste: se vão bem, volta a `CLOSED`; se não, volta a `OPEN` |

A configuração (`resilience4j.circuitbreaker.instances.frete` no `application.yaml`):

| Propriedade | Valor | Efeito |
| --- | --- | --- |
| `sliding-window-size` | 10 | O circuito olha as 10 últimas chamadas |
| `minimum-number-of-calls` | 10 | Só calcula a taxa com a janela cheia (o padrão é 100) |
| `failure-rate-threshold` | 50 | Abre com 50% de falhas |
| `slow-call-duration-threshold` / `slow-call-rate-threshold` | 2 s / 50 | Uma chamada acima de 2 s é lenta, e 50% de lentas também abre |
| `wait-duration-in-open-state` | 10 s | Tempo aberto |
| `permitted-number-of-calls-in-half-open-state` | 3 | Chamadas de teste |
| `automatic-transition-from-open-to-half-open-enabled` | true | Passa a `HALF_OPEN` sozinho, sem esperar a próxima chamada |

O estado fica no Actuator:

```bash
curl http://localhost:8080/actuator/circuitbreakers
curl http://localhost:8080/actuator/circuitbreakerevents/frete
```

### Experimentos

O WireMock lê `wiremock/mappings` ao subir (o frete normal: `200` com `{"valor":12.50,"prazoDias":3}`). Os arquivos de `wiremock/cenarios` sobrepõem esse stub quando aplicados (`priority: 1`). Medições feitas numa cópia isolada do projeto (Mongo, Kafka e WireMock próprios), com o mesmo código que está no repositório.

| # | Experimento | Como provocar | Resultado observado | Por quê |
| --- | --- | --- | --- | --- |
| 1 | **Sem proteção** (referência, antes do circuit breaker) | Cenário `erro-500` com 1 evento; cenário de 5 s de atraso com 1 evento; cenário `lento-2500ms` com 4 eventos de 4 clientes | O 500 levou o evento à DLT em **8,3 s**, sem pedido gravado. O atraso de 5 s estourou o timeout de leitura (3 s) 4 vezes e levou o evento à DLT em **19,5 s** (`ResourceAccessException`). Os 4 eventos lentos, em 3 partições, ficaram prontos em **10,7 s**, um a um, de 2,5 em 2,5 s | Uma dependência doente derrubava o consumer: o pedido ia para a DLT por culpa do frete, e a thread do listener, que atende todas as partições, ficava presa esperando |
| 2 | **Fechado** | Serviço saudável, 3 eventos | `CLOSED`, 3 chamadas, 0 falhas. Pedidos prontos em 0,6 s, com `frete` 12.50 e `freteEstimado` false | Sem falhas, o circuito não interfere |
| 3 | **Abertura** | Cenário `erro-500`, 10 eventos | `OPEN` na **10ª chamada** da janela (3 sucessos do experimento 2 e 7 falhas, taxa de 70%). Só 7 das 10 chamadas chegaram ao WireMock; as outras 3 foram desviadas. Os 10 pedidos foram gravados com `frete` 20.00 e `freteEstimado` true. **Nenhuma** mensagem na DLT | O circuito só calcula a taxa com a janela cheia (`minimum-number-of-calls`), então não abre no 5º erro. Depois de aberto, o fallback evita a exceção, e o evento segue |
| 4 | **Circuito aberto desvia** | Com o circuito aberto, mais 4 eventos | **Zero** chamadas ao WireMock, `naoPermitidas` subiu de 3 para 7, e os 4 pedidos saíram em 0,6 s (frete estimado) | Aberto, o circuito não toca no serviço doente e responde na hora, o que dá tempo para ele se recuperar |
| 5 | **Recuperação** | `reset` do WireMock e esperar | `OPEN` virou `HALF_OPEN` exatos **10,0 s** depois da abertura (17:32:21,792 → 17:32:31,793 nos eventos). Com 3 eventos de teste, voltou a `CLOSED` 0,3 s depois, e os pedidos vieram com `frete` 12.50 real | Em meio-aberto o circuito deixa passar só 3 chamadas. Como as 3 foram bem, ele fecha. Se uma falhasse, voltaria a abrir |
| 6 | **Lentidão (2,5 s)** | Cenário `lento-2500ms`, 10 eventos | 25,5 s no total. `OPEN` com **0 falhas e 10 chamadas lentas** (taxa de lentas 100%). Nenhuma mensagem na DLT | 2,5 s está acima do limite de lentidão (2 s) e abaixo do timeout (3 s): a chamada dá certo, mas é lenta, e o circuito abre por isso |
| 7 | **Atraso de 5 s** | Cenário de 5 s de atraso, 10 eventos | 29,9 s no total. A chamada estoura o timeout de leitura de 3 s, e o circuito abre com 10 falhas (e também 10 lentas) | Aqui o caminho é o da **falha**, e não o da lentidão. Sem o timeout, a thread ficaria presa por 5 s em cada chamada |

A saída de `circuitbreakerevents` desses experimentos está em [`experimentos/circuitbreakerevents-frete.json`](experimentos/circuitbreakerevents-frete.json): 22 `SUCCESS`, 17 `ERROR`, 7 `NOT_PERMITTED`, 2 `FAILURE_RATE_EXCEEDED`, 2 `SLOW_CALL_RATE_EXCEEDED` e 9 `STATE_TRANSITION`. O primeiro ciclo (experimentos 3 e 5):

```
17:32:21,792  CLOSED    -> OPEN
17:32:31,793  OPEN      -> HALF_OPEN   (10 s depois)
17:32:32,114  HALF_OPEN -> CLOSED      (3 chamadas de teste bem-sucedidas)
```

### Como reproduzir

Pare a API do Compose (`docker compose stop api`), gere o jar e rode a aplicação na porta 8098. Aplique um cenário, publique eventos (`POST /pedidos/eventos`, com um `clienteId` novo em cada um) e consulte o Actuator:

```bash
curl -X POST -H "Content-Type: application/json" --data "@wiremock/cenarios/erro-500.json" http://localhost:8089/__admin/mappings
curl http://localhost:8098/actuator/circuitbreakers
```

Para desfazer os cenários e voltar ao frete normal:

```bash
curl -X POST http://localhost:8089/__admin/mappings/reset
```

No PowerShell, use `curl.exe`. Se esquecer o `reset`, o cenário continua valendo.

### Retry ou circuit breaker?

O retry insiste: tenta de novo a mesma chamada, e é bom para falhas **passageiras** (um pico, uma conexão que caiu). Para um serviço **doente**, ele só piora: cada nova tentativa é mais carga em quem já não aguenta, e, num consumer de uma thread só, cada espera trava todas as partições (o experimento 1 mostra isso). O circuit breaker faz o oposto: depois de ver falhas demais, **para de chamar** e responde na hora com o fallback, dando tempo para o serviço voltar. Usá-los juntos faz sentido: um retry curto para o que é passageiro e o breaker para o que persiste. Pela ordem padrão do Resilience4j, o retry envolve o breaker, e então cada tentativa conta como chamada e pode abrir o circuito. Aqui só o circuit breaker está ligado; o retry da Etapa 5 continua no `DefaultErrorHandler`, mas, com o fallback, a exceção do frete nem chega até ele.

## Arquitetura hexagonal

O código se divide em três regiões, e as dependências só apontam para dentro:

```
adapter.in  (rest, kafka)  ──▶  application  ──▶  domain
                                    ▲
adapter.out (mongo, kafka, http) ───┘  implementam as portas de saída
```

| Pacote | O que tem | Pode depender de |
| --- | --- | --- |
| `domain` | `Pedido`, `ItemPedido`, `StatusPedido`, `Frete`, `PedidoCriadoEvento` e as regras: cálculo do total, transições de status | só o JDK |
| `application` | Os casos de uso (`port.in`), o que eles precisam do mundo (`port.out`) e a implementação (`service`) | domínio e o JDK |
| `adapter.in` | Controllers REST e listeners do Kafka: traduzem a entrada e chamam um caso de uso | aplicação e domínio |
| `adapter.out` | MongoDB, Kafka (producer) e HTTP (frete): implementam as portas de saída | aplicação e domínio |
| `config` | `CasosDeUsoConfig`: transforma os casos de uso em beans | tudo |

**Portas de entrada** (o que a aplicação oferece): `CriarPedidoUseCase`, `BuscarPedidoUseCase`, `ListarPedidosUseCase`, `AtualizarPedidoUseCase`, `RemoverPedidoUseCase`, `AceitarPedidoUseCase` (o `POST /pedidos/eventos`), `RegistrarPedidoCriadoUseCase` (o consumer A), `RegistrarAuditoriaUseCase` (o consumer B) e `RegistrarFalhaUseCase` (a DLT).
**Portas de saída** (o que a aplicação precisa): `PedidoRepositoryPort`, `FretePort`, `EventoPublisherPort`, `AuditoriaPort` e `FalhaPort`.

Um `POST /pedidos`, de ponta a ponta: o `PedidoController` valida o JSON (as anotações do Bean Validation ficam nos DTOs, que são do adapter), traduz para um comando e chama `CriarPedidoUseCase`. O `PedidoService` pede ao domínio um `Pedido.novo(...)` (que calcula o total) e o entrega ao `PedidoRepositoryPort`. Quem sabe que isso vira um documento no MongoDB é o `PedidoMongoAdapter`, que converte o `Pedido` em `PedidoDocument`.

### Como as fronteiras são mantidas

O `ArquiteturaTest` (ArchUnit) falha o build se:

- o `domain` depender de qualquer coisa fora do JDK (nem Spring, nem Jackson, nem Bean Validation);
- a `application` depender de um adapter ou de um framework;
- um adapter de entrada conhecer um de saída, ou o contrário;
- o MongoDB, o Kafka ou o Resilience4j aparecerem fora do adapter que os usa.

Além disso, `domain` e `application` **compilam só com o JDK**, sem nenhuma biblioteca no classpath:

```bash
javac -d out -cp "" $(find src/main/java/com/exemplo/pedidos/domain src/main/java/com/exemplo/pedidos/application -name '*.java')
```

E a troca do banco não toca no domínio: os testes dos casos de uso (`PedidoServiceTest`, `RegistrarPedidoCriadoServiceTest`) usam um `PedidoRepositoryEmMemoria`, um mapa que implementa a mesma porta, sem Spring, sem Mockito e sem Docker.

### O que mudou de lugar

| Antes | Depois |
| --- | --- |
| `Pedido` com `@Document` | `domain.Pedido` puro + `PedidoDocument` no adapter do Mongo |
| `PedidoService` verificava a transição de status | `Pedido.atualizar` verifica; o serviço só orquestra |
| `PedidoRepository` (Spring Data) usado em todo lugar | `PedidoRepositoryPort`; o Spring Data ficou escondido no adapter |
| Listeners chamavam repositório e cliente de frete | Listeners chamam um caso de uso; a regra (total negativo, frete, reentrega) está na aplicação |
| `FreteHttpAdapter` | `FreteHttpAdapter` implementa `FretePort`; o circuit breaker e o frete padrão do fallback continuam nele |
| `PedidoCriado` servia de evento e de contrato do tópico | `PedidoCriadoEvento` (domínio) e `PedidoCriado` (formato no Kafka), com tradução no adapter |
| `Page`/`Pageable` do Spring Data nos serviços | `Pagina` e `Paginacao`, da aplicação; o controller converte de e para o Spring |

## Deploy na nuvem (só o CRUD)

Kafka, consumers, DLT e o serviço de frete ficam só no ambiente local: a nuvem não enxerga o Docker do computador, e Kafka gerenciado gratuito praticamente não existe. Na nuvem sobe apenas o CRUD (`/pedidos`) com MongoDB Atlas.

```
GitHub Actions ──OIDC──▶ papel pedidos-github-deploy
     │ build da imagem
     ▼
    ECR ──▶ nova revisão da tarefa ──▶ ECS service (Fargate, perfil cloud)
                                              ▲            │ MONGODB_URI
Internet ──▶ load balancer :80 ───────────────┘            ▼ (Secrets Manager)
              health check /actuator/health        MongoDB Atlas (mongodb+srv, TLS)
```

### O perfil `cloud`

Ativado por `SPRING_PROFILES_ACTIVE=cloud` (a definição de tarefa já o define). Ele:

- exclui a autoconfiguração do Kafka (`application-cloud.yaml`) e as classes que dependem do Kafka ou do frete usam `@Profile("!cloud")`: listeners, publisher, `POST /pedidos/eventos`, configuração de erro e DLT, tópicos, `FreteClient`. Sem isso, a aplicação tentaria falar com um broker que não existe;
- lê o MongoDB só de `MONGODB_URI`, **sem valor padrão**. Se o segredo não chegar, a aplicação não sobe (o erro é `The connection string is invalid`, porque o Spring deixa o `${MONGODB_URI}` literal);
- expõe só `/actuator/health` e desliga o Swagger e o `/v3/api-docs`, porque a API fica na internet e sem autenticação;
- ativa o encerramento gracioso, para o deploy não cortar requisições em andamento.

Para ver o mesmo perfil localmente, com o MongoDB do Compose:

```bash
SPRING_PROFILES_ACTIVE=cloud MONGODB_URI="mongodb://pedidos:pedidos@localhost:27017/pedidos?authSource=admin" ./mvnw spring-boot:run
```

Na resposta de `GET /pedidos/{id}`, `frete` vem `null` e `freteEstimado` falso para os pedidos criados na nuvem: ninguém consulta o frete lá.

### O que existe em `infra/`

| Arquivo | Recursos |
| --- | --- |
| `rede.tf` | VPC própria com duas subnets públicas e sem NAT gateway; security group do load balancer (porta 80) e da tarefa (porta 8080, só a partir do load balancer) |
| `alb.tf` | Application Load Balancer, target group com health check em `/actuator/health` e listener HTTP |
| `ecr.tf` | Repositório de imagens (as 5 mais recentes ficam) |
| `segredos.tf` | O segredo `pedidos/mongodb-uri`, **sem valor**: a connection string não passa pelo Terraform |
| `ecs.tf` | Cluster, serviço Fargate, definição de tarefa inicial, logs no CloudWatch (7 dias) e o papel de execução, que só lê aquele segredo |
| `oidc.tf` | Provedor OIDC do GitHub e o papel `pedidos-github-deploy`, que só a branch `main` deste repositório pode assumir |
| `orcamento/` | Alerta no AWS Budgets, em um Terraform à parte para o `destroy` da API não apagá-lo |

### Passo a passo

1. **Alerta de custo, antes de tudo.** Em `infra/orcamento`: copie `terraform.tfvars.example` para `terraform.tfvars`, ponha o seu e-mail e rode `terraform init` e `terraform apply`. Avisa quando o gasto real passa de 50% e de 80% do limite (US$ 10 por padrão) e quando a previsão passa de 100%. O Budgets atrasa horas: é um alarme, não um freio.
2. **MongoDB Atlas.** Crie o cluster gratuito, um usuário com permissão `readWrite` só no banco `pedidos` e, em *Network Access*, a lista de IPs (veja o trade-off abaixo). Copie a connection string `mongodb+srv://...` **com `/pedidos` no caminho**.
3. **Infraestrutura.** Em `infra`: copie `terraform.tfvars.example` para `terraform.tfvars` (restrinja `cidrs_permitidos` ao seu IP, se puder) e rode `terraform init` e `terraform apply`. Se a conta já tiver o provedor OIDC do GitHub, use `criar_provedor_oidc = false`.
4. **Segredo.** Grave a connection string fora do Terraform:

   ```bash
   aws secretsmanager put-secret-value --secret-id pedidos/mongodb-uri --secret-string "mongodb+srv://USUARIO:SENHA@cluster.xxxxx.mongodb.net/pedidos"
   ```

5. **Variáveis do repositório** (Settings → Secrets and variables → Actions → *Variables*; não são segredos): `AWS_ROLE_ARN` e `AWS_REGION`, com os valores de `terraform output`.
6. **Deploy.** Com tudo isso na `main`, rode o workflow *Deploy* (Actions → Deploy → Run workflow). Até esse primeiro deploy, o serviço tenta subir a imagem `:inicial`, que não existe, e as tarefas falham em baixar a imagem: é esperado. O workflow constrói a imagem, envia ao ECR, registra uma revisão nova da tarefa e espera o serviço ficar estável (as tarefas saudáveis no load balancer).
7. **Teste.** Com a URL de `terraform output url_api`:

   ```bash
   curl -i -X POST http://URL/pedidos -H "Content-Type: application/json" -d '{"clienteId":"cli-1","itens":[{"produto":"caneta","quantidade":2,"precoUnitario":3.50}]}'
   curl -i http://URL/pedidos
   ```

8. **Desligar.** `terraform destroy` em `infra` remove tudo, inclusive as imagens do ECR e o segredo (sem janela de recuperação, senão o nome ficaria preso por 30 dias). O alerta do Budgets continua; apague-o em `infra/orcamento` quando quiser. Pause o cluster do Atlas ou apague-o no console.

### Custo

O load balancer cobra **por hora, mesmo sem tráfego**, e a tarefa Fargate também enquanto estiver rodando. Os preços mudam: confira a página de preços da AWS da região escolhida antes de subir e rode o `destroy` ao terminar. `quantidade_tarefas = 0` desliga a API sem destruir o resto, mas o load balancer continua cobrando.

### Trade-offs

- **`0.0.0.0/0` no Atlas.** As tarefas Fargate recebem um IP público diferente a cada início, e o Atlas só aceita uma lista de IPs. Liberar todos resolve para estudo, mas deixa o banco exposto à internet; o que o protege é o usuário com senha, a permissão mínima (`readWrite` em um banco) e o TLS. Em produção, o caminho é privado: VPC peering ou *private link* com o Atlas, e a lista de IPs fechada.
- **Sem NAT gateway.** Ele daria à tarefa um IP de saída fixo (que poderia entrar na lista do Atlas, no lugar de `0.0.0.0/0`), mas cobra por hora e seria o maior custo parado do projeto. Em troca, as tarefas ficam em subnet pública com IP público; quem as isola é o security group, que só aceita o load balancer.
- **HTTP puro.** Sem domínio próprio não há certificado no ACM. Em produção seriam HTTPS na 443 e redirecionamento da 80.
- **A API é pública e não tem autenticação.** Qualquer um com a URL grava pedidos. Por isso `cidrs_permitidos` existe: para estudo, deixe só o seu IP. O Swagger fica desligado na nuvem pelo mesmo motivo.
- **O segredo fora do Terraform.** O valor no `terraform.tfvars` ou no state iria parar em arquivo local em texto puro. O custo é um passo manual (o `put-secret-value`).
- **O health check inclui o MongoDB.** `/actuator/health` fica `DOWN` se o Atlas não responde, e o load balancer tira a tarefa de circulação. Isso evita servir erro, mas um problema no Atlas derruba a API inteira; um *liveness* separado do *readiness* resolveria, e não foi feito.
- **State local do Terraform.** Suficiente para estudo e para uma pessoa. Em equipe, o state ficaria em um bucket S3 com bloqueio.
- **Deploy manual e confiança restrita.** O `workflow_dispatch` evita que um merge crie custo sem querer. O papel da AWS só aceita o token da branch `main` deste repositório, então o workflow disparado de outra branch falha ao assumir o papel. As permissões do papel se limitam ao repositório de imagens, ao serviço e ao papel de execução.
- **O Terraform cria a definição de tarefa; o pipeline a substitui.** Cada deploy registra uma revisão nova com a imagem do commit, e o serviço ignora mudanças em `task_definition` para um `apply` não voltar para a imagem `:inicial`. Em compensação, mudar CPU, memória ou variáveis de ambiente pelo Terraform não chega ao serviço em execução sem um novo deploy.

## Testes

```bash
./mvnw verify
```

Os testes de integração usam Testcontainers e sobem um MongoDB 7.0, um Kafka e um WireMock (o frete simulado, com os mesmos stubs de `wiremock/mappings`) descartáveis (`@ServiceConnection` nos dois primeiros), então o Docker precisa estar rodando. Eles não usam o Mongo nem o Kafka do Compose. Os testes de unidade e os testes da camada web não precisam de Docker.

| Teste | O que prova |
| --- | --- |
| `StatusPedidoTest`, `PedidoTest` | Transições de status, cálculo do total no domínio, id atribuído uma só vez e lista de itens imutável |
| `PedidoServiceTest`, `PedidoEventoServiceTest`, `RegistrarPedidoCriadoServiceTest` | Os casos de uso, **sem Spring e sem Mockito**, sobre um repositório em memória: total calculado no servidor, 404, transição proibida sem alterar o pedido, paginação, evento publicado com os ids, e o consumer A (grava com o id do evento, reentrega ignorada sem desfazer um `PAGO`, total negativo recusado sem cotar o frete, frete estimado marcado) |
| `PedidoMongoAdapterIntegracaoTest` | O adapter do MongoDB contra um banco real: atribui o id, devolve o pedido completo, recusa `inserir` duplicado com `PedidoJaExisteException`, grava o alias `pedido` no lugar do nome da classe e **ainda lê um documento antigo** com `_class` de uma classe que não existe mais |
| `ArquiteturaTest` | As regras de dependência da seção "Arquitetura hexagonal" |
| `PedidoIntegracaoTest` | CRUD de ponta a ponta contra um MongoDB real |
| `PedidoEventoIntegracaoTest` | O evento chega ao tópico, com a chave do cliente, sem cabeçalhos de tipo, e o mesmo cliente cai na mesma partição |
| `PedidoEventoPublisherTest` | O adapter do Kafka converte as falhas do envio em `EventoNaoPublicadoException` |
| `PedidoEventoControllerTest` | `202`, `400` sem publicar e `503` quando a fila está fora |
| `PedidoCriadoPersistenciaListenerTest` | O listener do consumer A só traduz o registro do Kafka (`PedidoCriado`) para o evento de domínio e chama o caso de uso; a reentrega (`JA_EXISTIA`) não é erro e o `EventoInvalidoException` sobe para o tratamento de erros do Kafka |
| `PedidoCriadoAuditoriaListenerTest` | O consumer B registra `eventId`, partição, offset e instância |
| `ConsumersIntegracaoTest` | Um `POST /pedidos/eventos` vira pedido (consultável por `GET /pedidos/{pedidoId}`) e auditoria. O mesmo evento entregue duas vezes gera um pedido e duas auditorias. **A reentrega não desfaz uma alteração**: um pedido pago continua `PAGO` depois de o evento voltar |
| `FalhasIntegracaoTest` | Um evento com `total` negativo vai para a DLT depois das tentativas, é registrado em `mensagens_falhas` e não vira pedido. Uma mensagem malformada vai para a DLT com o texto original (uma vez por group) e a mensagem válida que vem depois, na mesma partição, é processada |
| `FreteIntegracaoTest` | O frete normal chega ao pedido (`12.50`, não estimado) e o circuito fica fechado. Com o serviço devolvendo 500, as 10 primeiras chamadas abrem o circuito, as seguintes **não tocam no serviço** (o WireMock conta as requisições) e valem o frete padrão. Com o serviço recuperado e o circuito em meio-aberto, 3 chamadas de teste o fecham. Uma resposta acima de 2 s conta como chamada lenta, e não como falha. De ponta a ponta: com o serviço fora do ar, o pedido é gravado com `freteEstimado` verdadeiro e **não** vai para a DLT |

| `PerfilCloudIntegracaoTest` | Com o perfil `cloud` e **só um MongoDB** (sem container de Kafka), o contexto sobe sem `KafkaTemplate`, listeners nem `FretePort`; o CRUD funciona; `POST /pedidos/eventos` deixa de existir (`405`); só `/actuator/health` está exposto, e o Swagger e o `/v3/api-docs` respondem `404` |

Os consumers rodam em outra thread, então os testes que dependem deles **esperam com Awaitility** (`await().atMost(...).untilAsserted(...)`) em vez de `Thread.sleep`: conferem repetidamente e terminam assim que a condição passa. Os testes de eventos e de consumers usam `clienteId` e ids próprios, porque o Mongo e o Kafka de teste são compartilhados entre as classes, e nenhum teste confere o tamanho da coleção inteira. Para checar que a suíte não depende da ordem de execução:

```bash
./mvnw test "-Dsurefire.runOrder=reversealphabetical"
```

No PowerShell, argumentos `-D` com ponto no nome precisam de aspas.

Dois cuidados nos testes de falha e reentrega:

- **Sentinela em vez de espera fixa.** Para provar que uma reentrega *não* mudou nada, o teste precisa saber que ela já foi processada. Esperar a auditoria não serve (é outro group), e dormir é frágil. O teste publica depois da reentrega uma mensagem sentinela com a mesma chave: a mesma partição é lida em ordem, então, quando a sentinela aparece no banco, a reentrega anterior já passou.
- **Um produtor de bytes crus para o veneno.** O `KafkaTemplate` da aplicação sempre serializa para JSON, então não consegue publicar uma mensagem malformada. O teste cria um produtor de `byte[]` apontado para o Kafka do Testcontainers.

`FalhasIntegracaoTest` usa as mesmas anotações de `ConsumersIntegracaoTest`, para o Spring reaproveitar o contexto e os containers entre as duas classes.

## Estrutura

```
src/main/java/com/exemplo/pedidos
├── PedidosApplication.java
├── domain                                               sem Spring, sem Jackson, sem nada além do JDK
│   ├── Pedido.java, ItemPedido.java, StatusPedido.java  o pedido e suas regras
│   ├── Frete.java                                       valor, prazo e o frete padrão do fallback
│   ├── PedidoCriadoEvento.java                          o fato "pedido criado", na linguagem do negócio
│   └── TransicaoStatusInvalidaException.java
├── application
│   ├── port.in                                          casos de uso (CriarPedidoUseCase, AceitarPedidoUseCase, ...)
│   ├── port.out                                         PedidoRepositoryPort, FretePort, EventoPublisherPort, AuditoriaPort, FalhaPort
│   ├── service                                          implementação dos casos de uso (classes comuns, sem anotações)
│   ├── erro                                             PedidoNaoEncontradoException, PedidoJaExisteException, ...
│   └── Pagina.java, Paginacao.java, RegistroAuditoria.java, RegistroFalha.java
├── adapter
│   ├── in.rest                                          PedidoController, PedidoEventoController, GlobalExceptionHandler, dto
│   ├── in.kafka                                         listeners (consumers A e B, DLT) e KafkaErroConfig (retry e DLT)
│   ├── kafka                                            o que as duas pontas do Kafka dividem: PedidoCriado (formato no tópico) e KafkaTopicConfig
│   ├── out.mongo                                        PedidoDocument e os adapters que implementam as portas de persistência
│   ├── out.kafka                                        PedidoEventoPublisher (única classe que fala com o KafkaTemplate)
│   └── out.http                                         FreteHttpAdapter (circuit breaker e fallback), FreteConfig, FreteProperties, CotacaoFrete
└── config
    └── CasosDeUsoConfig.java                            cria os beans dos casos de uso

src/main/resources
├── application.yaml                                     configuração local (Kafka, frete, circuit breaker)
└── application-cloud.yaml                               perfil cloud: sem Kafka, MongoDB por MONGODB_URI

infra                                                    Terraform da AWS (veja "Deploy na nuvem")
└── orcamento                                            alerta de custo, com state próprio

.github/workflows
├── ci.yml                                               build e testes, validação do Terraform e imagem Docker
└── deploy.yml                                           deploy manual na AWS, por OIDC
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
- **O consumer A usa o `pedidoId` do evento como `_id` e grava com `insert`.** Na Etapa 4 era `save`, que substitui o documento de mesmo id: não duplicava, mas a reentrega desfazia um pagamento (experimento 6 de "Falhas"). Com `insert`, o banco recusa o `_id` repetido, e o consumer trata o `DuplicateKeyException` como "já processei" (veja "Idempotência" abaixo).
- **O `total` não é recalculado no consumer.** Ele confia no contrato: quem calcula é o servidor, ao montar o evento.
- **A auditoria guarda a `instancia`.** É o campo que permite provar, depois do fato, qual instância processou qual partição. Sem ele, os experimentos só se veriam nos logs.
- **Testes assíncronos esperam, não dormem.** `Thread.sleep` é lento sempre e frágil quando o consumer demora mais que o previsto. O Awaitility para na hora em que a condição passa e só falha se estourar o limite.
- **Testes não contam coleções compartilhadas.** Com um consumer gravando em `pedidos`, "a coleção tem exatamente 1 pedido" deixa de ser verdade. Os testes verificam o pedido do cliente que eles mesmos criaram. Tentei provocar a corrida com um consumer lento e não consegui reproduzi-la, porque a janela é de poucas centenas de milissegundos, mas contar uma coleção compartilhada com um escritor assíncrono continua frágil por construção.
- **Kafka isolado em uma classe.** Só o `PedidoEventoPublisher` conhece o `KafkaTemplate`, o que facilita testar as falhas com um dublê. Desde a Etapa 8 ele implementa `EventoPublisherPort`, e o ArchUnit garante que nada mais importe o Kafka fora dos adapters.
- **Dois listeners no Kafka** (`kafka:9092` e `localhost:9094`), explicados em "Como rodar".
- **Credenciais padrão `pedidos`/`pedidos`.** Só para desenvolvimento local. Em qualquer ambiente real, vêm de variáveis de ambiente ou de um gerenciador de segredos.
- **Retry com espera crescente, 3 tentativas (1 s, 2 s, 4 s).** Cobre falhas passageiras sem martelar o recurso que está doente. O custo é que a tentativa **bloqueia o consumer inteiro**: com `concurrency` 1, a thread do listener atende as 3 partições, e nenhuma é lida durante os cerca de 7 s (na Etapa 6, quatro eventos lentos em três partições foram atendidos um a um). A alternativa é um tópico de retry separado (`@RetryableTopic`), que não bloqueia, mas multiplica tópicos e a ordem por chave deixa de valer.
- **Nome da DLT explícito: `pedidos.criados.DLT`, na mesma partição de origem.** O nome padrão do Spring Kafka 4 é `pedidos.criados-dlt`. Com ele, a falha seria publicada em um tópico criado sozinho pelo broker, com 1 partição, sem que nada avisasse. O resolvedor explícito também mantém a ordem por partição entre o original e a DLT, e o tópico é declarado como `NewTopic`.
- **Guarda contra loop na DLT.** Registro que já está na DLT e falha de novo é descartado, e não republicado (experimentos 2 e 3). Perder o registro dessa falha é preferível a um laço infinito.
- **`ErrorHandlingDeserializer` com `JacksonJsonDeserializer` como delegado, e dois templates na DLT.** Sem ele, uma única mensagem malformada trava a partição e enche o disco (experimento 4). A DLT usa um template de `byte[]` para mensagens que nunca viraram objeto, e o de JSON para o resto; a ordem do `LinkedHashMap` importa, porque `byte[]` precisa ser testado antes de `Object`. O `DeadLetterListener` lê o valor como texto, então o `payload` fica legível em `mensagens_falhas`.
- **O template de `byte[]` herda a configuração do produtor JSON.** A primeira versão o criava a partir de `KafkaProperties`, que só lê o `application.yaml` e ignora o endereço que o Testcontainers injeta (`@ServiceConnection`). Em produção funcionava, porque o endereço vem do yaml, mas no teste o veneno ia para `localhost:9094`, a DLT nunca recebia e a partição ficava presa. O teste de integração descobriu o erro. Agora o template parte de `getProducerFactory().getConfigurationProperties()` do template JSON, que já traz o endereço certo.
- **Idempotência pela chave natural `pedidoId`, e não por um `eventId` guardado à parte.** Na API os dois são 1 para 1, e o `pedidoId` já é o `_id`, então o banco garante a unicidade com **uma** gravação. Uma coleção de "eventos processados" exigiria **duas** (marcar o evento e gravar o pedido), e o Mongo do Compose é um nó só, sem transação entre elas: uma queda entre as duas deixaria o evento marcado sem virar pedido, perdido para sempre.
- **Limites da idempotência.** (1) O `insert` ignora **qualquer** segundo evento com o mesmo `pedidoId`, e não só reentregas; um produtor com bug que reutilizasse o id teria o segundo evento descartado (com um `warn` no log). (2) Proteger contra a reentrega do evento não protege contra o cliente que repete o `POST /pedidos/eventos`: cada chamada gera ids novos e, portanto, outro pedido. Isso exigiria uma chave de idempotência enviada pelo cliente.
- **Entrega *pelo menos uma vez*.** O offset só é confirmado depois de o listener terminar. Se o processo cai entre gravar e confirmar, a mensagem volta (experimento 8). Nenhum sistema evita isso; o que se faz é tornar o reprocessamento inofensivo. Idempotência, aqui, quer dizer **não corromper**, e não só "não duplicar".
- **A DLT só registra, não reprocessa.** `mensagens_falhas` guarda o suficiente (origem, texto, exceção e motivo) para reenviar ou corrigir à mão. Um reprocessamento automático ficaria para depois. O `total` negativo é uma regra criada para exercitar o caminho de falha: a API nunca o produz, porque o servidor calcula o total.
- **CI: falha intermitente na subida do container do MongoDB; o `mongod` se recusa a iniciar em kernels Linux 6.19 ou mais novos.** O mesmo commit falhou na primeira tentativa e passou na segunda, em `ubuntu-latest` (26.04) e em `ubuntu-24.04`, sempre na criação do container `mongo:8` pelo Testcontainers. O log completo do PR da Etapa 5 mostra o sintoma: o `mongo:8` "inicia" em 0,2 s, o `exec` de verificação do Testcontainers falha (`procReady not received` na primeira vez, código 137 na segunda) e o container já não está rodando quando o Spring pede a URL (`MongoDBContainer should be started first`). Isso se repetiu nas **duas** subidas do container no mesmo job, enquanto o Kafka subiu normalmente em cerca de 3 s; os 12 testes que falharam são consequência, e o código da Etapa 5 não participa. Um passo de diagnóstico no CI mostrou a causa: a CPU tem AVX (AMD EPYC 9V74), então a suspeita de hardware estava **errada**, e o `mongod --version` terminou com `MongoDB cannot start: Linux kernel versions 6.19 and newer has a known incompatibility with this version of MongoDB` (SERVER-121912). É uma trava deliberada das versões 8.x: uma mudança do kernel (rseq) quebra o alocador tcmalloc, e o `mongod` prefere não subir. A correção do kernel veio na 7.0.14, e o MongoDB só passou a aceitá-lo nas versões 8.0.30 e 8.3.9 (SERVER-125742). A falha é intermitente, provavelmente, porque as máquinas virtuais do runner nem sempre têm o mesmo kernel. O runner foi fixado em `ubuntu-24.04` por **reprodutibilidade** (o `ubuntu-latest` estava migrando de versão), e isso **não** foi a correção. O runner tem o kernel `7.0.0-1017-azure`: uma numeração que o MongoDB provavelmente não reconhece como a 7.0.14 corrigida (os kernels da Ubuntu numeram de outro jeito, e o MongoDB tem um ticket aberto sobre isso). No diagnóstico, `mongo:8.0`, `mongo:8.3.9` e `mongo:8` falharam e `mongo:7.0` (7.0.45) rodou. **Correção:** os testes de integração usam `mongo:7.0` (`TestcontainersConfiguration`) e a suíte passa inteira nela. O Compose continua em `mongo:8`: o volume local já foi criado pela versão 8, e a 7.0 não abre esses dados. A consequência é que testes e Compose rodam versões diferentes do MongoDB; quando o runner (ou o MongoDB) corrigir a detecção do kernel, o ideal é voltar os testes para a mesma versão do Compose.
- **O frete é consultado antes do `insert`, dentro do consumer A.** Numa reentrega do mesmo evento, a consulta se repete antes de o `insert` descobrir que o pedido já existe: é um custo, e não um erro. A alternativa seria checar a existência do pedido antes de chamar o serviço.
- **Contrato HTTP e tipo de domínio separados (`CotacaoFrete` e `Frete`).** O `CotacaoFrete` espelha o JSON do serviço. O fallback precisa dizer "este frete é estimado", e a primeira tentativa foi acrescentar um `boolean estimado` ao contrato: o Jackson 3 recusa o campo ausente (`Cannot map null into type boolean`, porque `FAIL_ON_NULL_FOR_PRIMITIVES` vem ligado). O `Frete` guarda o que o pedido precisa, inclusive o frete padrão.
- **Fallback com frete padrão, e não exceção.** O frete é uma estimativa, então um valor padrão marcado como estimado é aceitável, e o pedido não pode depender da disponibilidade de um serviço de cotação. Para uma dependência em que o valor padrão **não** serve (cobrança, autorização), o fallback correto é falhar de forma explícita ou enfileirar para tentar depois, e nunca inventar um resultado.
- **Dois mecanismos para abrir: falha e lentidão.** O timeout de leitura é de 3 s e o limite de lentidão é de 2 s, de propósito: uma resposta de 2,5 s é lenta mas bem-sucedida (abre pela taxa de lentas), e uma de 5 s estoura o timeout (abre pela taxa de falhas). Sem timeout, o limite de lentidão nunca seria alcançado por uma chamada pendurada.
- **`minimum-number-of-calls: 10` e `automatic-transition-from-open-to-half-open-enabled`.** O padrão do primeiro é 100, o que faria o circuito demorar a abrir numa janela de 10. O segundo faz o `HALF_OPEN` aparecer no Actuator sem depender de tráfego.
- **O estado do circuito é por instância da aplicação.** Fica em memória, na JVM. Duas instâncias do consumer abrem e fecham o circuito cada uma por si, sem coordenação.
- **Limitação conhecida: erros 4xx também contam como falha.** O Resilience4j registra qualquer exceção por padrão, inclusive o `HttpClientErrorException` de um 400 ou 404. Se o serviço respondesse 4xx por culpa do chamador, o circuito poderia abrir sem o serviço estar doente. O ajuste seria `ignore-exceptions` para essa classe, que não foi aplicado.
- **ECS Fargate com Terraform, e não Elastic Beanstalk.** O Beanstalk daria menos trabalho, mas esconderia a rede, os papéis e o balanceador que a Etapa 7 quer mostrar. O Lambda foi descartado: exigiria adaptar o Spring e sofreria com a partida a frio da JVM.
- **Perfil `cloud` por `@Profile("!cloud")`, e não por uma propriedade `kafka.enabled`.** O roteiro pedia um perfil, e ele já agrupa a configuração da nuvem em um arquivo. O custo é que a marcação está espalhada em 10 classes: quem criar um novo componente de Kafka precisa lembrar dela. O `PerfilCloudIntegracaoTest` pega o esquecimento, porque o contexto de teste não tem broker.
- **A configuração da nuvem não esconde falha.** `MONGODB_URI` sem padrão e `application-cloud.yaml` sem caminho para o MongoDB local: uma API da nuvem que caísse silenciosamente no `localhost` seria pior do que uma que não sobe.
- **Hexagonal num CRUD é exagero, e vale pelo que ensina.** Antes da Etapa 8, o `PedidoService` era curto e fazia tudo bem. Agora há 9 portas de entrada, 5 de saída e conversões entre `Pedido`, `PedidoDocument`, `PedidoResponse` e `PedidoCriado`. O ganho é real em três lugares: a regra de transição saiu do serviço para o domínio, os casos de uso se testam sem banco e sem Spring, e o Kafka e o Mongo ficaram atrás de interfaces. Para um CRUD que nunca trocaria de banco, o custo seria maior que o benefício.
- **A aplicação não usa anotações do Spring.** Os serviços são classes comuns, ligadas em `CasosDeUsoConfig`. Seria mais curto anotá-los com `@Service`, mas a regra "a aplicação só depende do domínio" deixaria de poder ser verificada pelo ArchUnit, e os testes dos casos de uso são, por isso, simples `new`.
- **Três casos de uso são só repasse.** `RegistrarAuditoriaUseCase` e `RegistrarFalhaUseCase` apenas chamam a porta de saída, e o `PedidoService` implementa cinco interfaces. A fronteira vale mesmo sem lógica: o listener deixa de conhecer o MongoDB. O repasse não é disfarce para regra de negócio, e quando a auditoria ganhar uma regra, ela já tem para onde ir.
- **O id nasce na persistência.** O domínio não gera o id do pedido criado pela API: `Pedido.novo` devolve um pedido sem id, e o adapter chama `atribuirId` com o `ObjectId` gerado pelo MongoDB, uma vez só. A alternativa (o domínio gerar um UUID) mudaria o formato dos ids já gravados.
- **`@TypeAlias` nos documentos.** O Spring Data grava o nome da classe no campo `_class`, e a refatoração trocou todos os pacotes. Documentos antigos continuam legíveis (o teste do adapter insere um e o lê), porque o Spring ignora um `_class` que não consegue carregar e usa o tipo declarado. Os documentos novos gravam um alias fixo (`pedido`, `auditoria_evento`, `mensagem_falha`), que não depende do pacote.
- **Um erro que só um teste de integração pegou.** O `application.yaml` tinha o nome completo da classe `PedidoCriado` (`spring.json.value.default.type`), e a mudança de pacote só quebrou na subida do consumer, e não na compilação. Nome de classe em texto não é verificado pelo compilador.
- **O WireMock serve ao Compose e aos testes.** Os mesmos stubs de `wiremock/mappings` sobem no Compose e dentro do `TestcontainersConfiguration`, e os testes aplicam os cenários de `wiremock/cenarios` pela API administrativa. O estado do circuito e o WireMock são compartilhados entre as classes que reaproveitam o contexto do Spring, então o `FreteIntegracaoTest` restaura os dois antes e depois de cada teste.

## Roteiro

- [x] Etapa 1: CRUD com MongoDB, validação e erros padronizados
- [x] Etapa 2: testes com Testcontainers e CI no GitHub Actions
- [x] Etapa 3: Kafka, producer
- [x] Etapa 4: consumers e consumer groups
- [x] Etapa 5: retry, dead-letter e idempotência
- [x] Etapa 6: circuit breaker com Resilience4j
- [x] Etapa 7: deploy do CRUD na nuvem
- [x] Etapa 8 (opcional): arquitetura hexagonal

## Licença

MIT. Veja [LICENSE](LICENSE).
