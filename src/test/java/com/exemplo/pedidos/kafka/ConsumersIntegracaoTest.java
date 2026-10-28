package com.exemplo.pedidos.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import com.exemplo.pedidos.TestcontainersConfiguration;
import com.exemplo.pedidos.auditoria.AuditoriaEvento;
import com.exemplo.pedidos.auditoria.AuditoriaEventoRepository;
import com.exemplo.pedidos.pedido.Pedido;
import com.exemplo.pedidos.pedido.PedidoRepository;
import com.exemplo.pedidos.pedido.StatusPedido;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ConsumersIntegracaoTest {

    private static final Duration ESPERA_MAXIMA = Duration.ofSeconds(20);

    @Autowired
    MockMvcTester mvc;

    @Autowired
    KafkaTemplate<String, PedidoCriado> kafkaTemplate;

    @Autowired
    PedidoRepository pedidoRepository;

    @Autowired
    AuditoriaEventoRepository auditoriaRepository;

    @Test
    void eventoPublicadoViraPedidoEAuditoria() throws Exception {
        String corpo = mvc.post().uri("/pedidos/eventos")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"clienteId":"cli-consumer","itens":[{"produto":"caneta","quantidade":2,"precoUnitario":3.50}]}
                        """)
                .exchange()
                .getResponse().getContentAsString();
        String pedidoId = campo(corpo, "pedidoId");
        String eventId = campo(corpo, "eventId");

        await().atMost(ESPERA_MAXIMA).untilAsserted(() ->
                assertThat(pedidoRepository.findById(pedidoId)).isPresent());

        Pedido pedido = pedidoRepository.findById(pedidoId).orElseThrow();
        assertThat(pedido.getClienteId()).isEqualTo("cli-consumer");
        assertThat(pedido.getStatus()).isEqualTo(StatusPedido.CRIADO);
        assertThat(pedido.getTotal()).isEqualByComparingTo("7.00");

        await().atMost(ESPERA_MAXIMA).untilAsserted(() ->
                assertThat(auditoriasDo(eventId)).hasSize(1));

        AuditoriaEvento auditoria = auditoriasDo(eventId).get(0);
        assertThat(auditoria.clienteId()).isEqualTo("cli-consumer");
        assertThat(auditoria.particao()).isBetween(0, 2);
        assertThat(auditoria.offset()).isGreaterThanOrEqualTo(0);

        // o 202 devolvido pela API e o pedido gravado pelo consumer fecham o ciclo
        assertThat(mvc.get().uri("/pedidos/" + pedidoId)).hasStatus(HttpStatus.OK);
    }

    @Test
    void mesmoEventoDuasVezesGravaUmPedidoEDuasAuditorias() throws Exception {
        PedidoCriado evento = new PedidoCriado(UUID.randomUUID(), UUID.randomUUID(), "cli-duplicado",
                List.of(new PedidoCriado.Item("caneta", 2, new BigDecimal("3.50"))),
                new BigDecimal("7.00"), Instant.now());

        kafkaTemplate.send(KafkaTopicConfig.PEDIDOS_CRIADOS, evento.clienteId(), evento).get(10, TimeUnit.SECONDS);
        kafkaTemplate.send(KafkaTopicConfig.PEDIDOS_CRIADOS, evento.clienteId(), evento).get(10, TimeUnit.SECONDS);

        String eventId = evento.eventId().toString();
        await().atMost(ESPERA_MAXIMA).untilAsserted(() ->
                assertThat(auditoriasDo(eventId)).hasSize(2));

        assertThat(pedidoRepository.findAll())
                .filteredOn(p -> p.getId().equals(evento.pedidoId().toString()))
                .hasSize(1);
    }

    private List<AuditoriaEvento> auditoriasDo(String eventId) {
        return auditoriaRepository.findAll().stream()
                .filter(a -> eventId.equals(a.eventId()))
                .toList();
    }

    private static String campo(String json, String nome) {
        Matcher m = Pattern.compile("\"" + nome + "\":\"([^\"]+)\"").matcher(json);
        assertThat(m.find()).as("campo %s no corpo %s", nome, json).isTrue();
        return m.group(1);
    }
}