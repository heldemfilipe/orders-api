package com.exemplo.pedidos.frete;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.testcontainers.containers.GenericContainer;

import com.exemplo.pedidos.TestcontainersConfiguration;
import com.exemplo.pedidos.application.port.out.FretePort;
import com.exemplo.pedidos.domain.Frete;
import com.exemplo.pedidos.adapter.out.mongo.MensagemFalhaRepository;
import com.exemplo.pedidos.adapter.kafka.KafkaTopicConfig;
import com.exemplo.pedidos.adapter.kafka.PedidoCriado;
import com.exemplo.pedidos.domain.Pedido;
import com.exemplo.pedidos.application.port.out.PedidoRepositoryPort;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

// mesmas anotacoes do ConsumersIntegracaoTest: o Spring reaproveita o contexto (e os containers)
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class FreteIntegracaoTest {

    private static final Duration ESPERA_MAXIMA = Duration.ofSeconds(20);

    @Autowired
    FretePort freteClient;

    @Autowired
    CircuitBreakerRegistry registro;

    @Autowired
    @Qualifier("freteContainer")
    GenericContainer<?> wiremock;

    @Autowired
    KafkaTemplate<String, PedidoCriado> kafkaTemplate;

    @Autowired
    PedidoRepositoryPort pedidoRepository;

    @Autowired
    MensagemFalhaRepository falhaRepository;

    private RestClient admin;
    private CircuitBreaker circuito;

    @BeforeEach
    void prepara() {
        admin = RestClient.create("http://" + wiremock.getHost() + ":" + wiremock.getMappedPort(8080));
        circuito = registro.circuitBreaker("frete");
        restaurar();
        circuito.reset();
    }

    // o circuito e o WireMock sao compartilhados com os outros testes do mesmo contexto
    @AfterEach
    void limpa() {
        restaurar();
        circuito.reset();
    }

    @Test
    void cotacaoNormalDevolveOValorDoServicoComOCircuitoFechado() {
        Frete frete = freteClient.cotar("cli-frete-ok");

        assertThat(frete.valor()).isEqualByComparingTo("12.50");
        assertThat(frete.prazoDias()).isEqualTo(3);
        assertThat(frete.estimado()).isFalse();
        assertThat(circuito.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    void falhasAbremOCircuitoEAsChamadasSeguintesNaoTocamNoServico() throws Exception {
        aplicarCenario("erro-500.json");

        for (int i = 0; i < 10; i++) {
            assertThat(freteClient.cotar("cli-frete-erro")).isEqualTo(Frete.PADRAO);
        }
        assertThat(circuito.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        assertThat(requisicoesRecebidas()).isEqualTo(10);

        for (int i = 0; i < 3; i++) {
            assertThat(freteClient.cotar("cli-frete-aberto")).isEqualTo(Frete.PADRAO);
        }
        assertThat(requisicoesRecebidas()).isEqualTo(10);
        assertThat(circuito.getMetrics().getNumberOfNotPermittedCalls()).isEqualTo(3);
    }

    @Test
    void servicoRecuperadoFechaOCircuitoDepoisDasChamadasDeTeste() throws Exception {
        aplicarCenario("erro-500.json");
        for (int i = 0; i < 10; i++) {
            freteClient.cotar("cli-frete-recupera");
        }
        assertThat(circuito.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        restaurar();
        circuito.transitionToHalfOpenState();
        for (int i = 0; i < 3; i++) {
            assertThat(freteClient.cotar("cli-frete-recupera").estimado()).isFalse();
        }

        assertThat(circuito.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    void respostaMaisLentaQueOLimiteContaComoChamadaLenta() throws Exception {
        aplicarCenario("lento-2500ms.json");

        for (int i = 0; i < 2; i++) {
            assertThat(freteClient.cotar("cli-frete-lento").estimado()).isFalse();
        }

        assertThat(circuito.getMetrics().getNumberOfSlowCalls()).isEqualTo(2);
        assertThat(circuito.getMetrics().getNumberOfFailedCalls()).isZero();
    }

    @Test
    void eventoComFreteDoServicoGravaPedidoComFreteReal() {
        PedidoCriado evento = evento("cli-frete-pedido-ok");

        publicar(evento);

        Pedido pedido = esperarPedido(evento);
        assertThat(pedido.getFrete()).isEqualByComparingTo("12.50");
        assertThat(pedido.isFreteEstimado()).isFalse();
    }

    @Test
    void servicoForaDoArGravaPedidoComFreteEstimadoSemIrParaADlt() throws Exception {
        aplicarCenario("erro-500.json");
        PedidoCriado evento = evento("cli-frete-pedido-estimado");

        publicar(evento);

        Pedido pedido = esperarPedido(evento);
        assertThat(pedido.getFrete()).isEqualByComparingTo("20.00");
        assertThat(pedido.isFreteEstimado()).isTrue();
        assertThat(falhaRepository.findAll()).noneMatch(f -> "cli-frete-pedido-estimado".equals(f.chave()));
    }

    private void aplicarCenario(String arquivo) throws IOException {
        String json = Files.readString(Path.of("wiremock/cenarios", arquivo));
        admin.post().uri("/__admin/mappings").contentType(MediaType.APPLICATION_JSON).body(json)
                .retrieve().toBodilessEntity();
    }

    private void restaurar() {
        admin.post().uri("/__admin/mappings/reset").retrieve().toBodilessEntity();
        admin.delete().uri("/__admin/requests").retrieve().toBodilessEntity();
    }

    private int requisicoesRecebidas() throws Exception {
        String corpo = admin.get().uri("/__admin/requests").retrieve().body(String.class);
        return JsonMapper.builder().build().readTree(corpo).path("meta").path("total").asInt();
    }

    private PedidoCriado evento(String cliente) {
        return new PedidoCriado(UUID.randomUUID(), UUID.randomUUID(), cliente,
                List.of(new PedidoCriado.Item("caneta", 2, new BigDecimal("3.50"))),
                new BigDecimal("7.00"), Instant.now());
    }

    private void publicar(PedidoCriado evento) {
        try {
            kafkaTemplate.send(KafkaTopicConfig.PEDIDOS_CRIADOS, evento.clienteId(), evento)
                    .get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private Pedido esperarPedido(PedidoCriado evento) {
        String id = evento.pedidoId().toString();
        await().atMost(ESPERA_MAXIMA).untilAsserted(() ->
                assertThat(pedidoRepository.buscarPorId(id)).isPresent());
        return pedidoRepository.buscarPorId(id).orElseThrow();
    }
}
