package com.exemplo.pedidos.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.testcontainers.kafka.KafkaContainer;

import com.exemplo.pedidos.TestcontainersConfiguration;
import com.exemplo.pedidos.adapter.kafka.KafkaTopicConfig;
import com.exemplo.pedidos.adapter.kafka.PedidoCriado;
import com.exemplo.pedidos.adapter.out.mongo.MensagemFalha;
import com.exemplo.pedidos.adapter.out.mongo.MensagemFalhaRepository;
import com.exemplo.pedidos.application.port.out.PedidoRepositoryPort;

// mesmas anotacoes do ConsumersIntegracaoTest: o Spring reaproveita o contexto (e os containers)
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class FalhasIntegracaoTest {

    // 3 tentativas com espera de 1s, 2s e 4s antes de ir para a DLT
    private static final Duration ESPERA_MAXIMA = Duration.ofSeconds(40);

    @Autowired
    KafkaTemplate<String, PedidoCriado> kafkaTemplate;

    @Autowired
    KafkaContainer kafka;

    @Autowired
    PedidoRepositoryPort pedidoRepository;

    @Autowired
    MensagemFalhaRepository falhaRepository;

    @Test
    void totalNegativoVaiParaDltDepoisDasTentativasSemGravarPedido() throws Exception {
        String cliente = "cli-negativo-" + UUID.randomUUID();
        PedidoCriado evento = evento(cliente, new BigDecimal("-5.00"));

        kafkaTemplate.send(KafkaTopicConfig.PEDIDOS_CRIADOS, cliente, evento).get(10, TimeUnit.SECONDS);

        await().atMost(ESPERA_MAXIMA).untilAsserted(() ->
                assertThat(falhasDe(cliente)).hasSize(1));

        MensagemFalha falha = falhasDe(cliente).get(0);
        assertThat(falha.topicoOriginal()).isEqualTo(KafkaTopicConfig.PEDIDOS_CRIADOS);
        assertThat(falha.grupoOriginal()).isEqualTo("persistencia");
        assertThat(falha.excecao()).endsWith("EventoInvalidoException");
        assertThat(falha.motivo()).contains("total negativo");
        assertThat(falha.payload()).contains(evento.pedidoId().toString());

        assertThat(pedidoRepository.buscarPorId(evento.pedidoId().toString())).isEmpty();
    }

    @Test
    void mensagemMalformadaVaiParaDltComOsBytesOriginaisEAParticaoSegue() throws Exception {
        String cliente = "cli-veneno-" + UUID.randomUUID();
        String lixo = "{isto nao e um json valido";

        publicarBytes(cliente, lixo);
        PedidoCriado valido = evento(cliente, new BigDecimal("7.00"));
        kafkaTemplate.send(KafkaTopicConfig.PEDIDOS_CRIADOS, cliente, valido).get(10, TimeUnit.SECONDS);

        // a mensagem valida, na mesma particao e depois do veneno, nao fica presa atras dele
        await().atMost(ESPERA_MAXIMA).untilAsserted(() ->
                assertThat(pedidoRepository.buscarPorId(valido.pedidoId().toString())).isPresent());

        // os dois groups (persistencia e auditoria) falham ao ler o mesmo veneno
        await().atMost(ESPERA_MAXIMA).untilAsserted(() ->
                assertThat(falhasDe(cliente)).hasSize(2));

        List<MensagemFalha> falhas = falhasDe(cliente);
        assertThat(falhas).extracting(MensagemFalha::grupoOriginal)
                .containsExactlyInAnyOrder("persistencia", "auditoria");
        assertThat(falhas).extracting(MensagemFalha::payload).containsOnly(lixo);
    }

    private PedidoCriado evento(String cliente, BigDecimal total) {
        return new PedidoCriado(UUID.randomUUID(), UUID.randomUUID(), cliente,
                List.of(new PedidoCriado.Item("caneta", 2, new BigDecimal("3.50"))),
                total, Instant.now());
    }

    // o template da aplicacao serializa para JSON; para publicar lixo precisamos enviar os bytes crus
    private void publicarBytes(String chave, String texto) throws Exception {
        DefaultKafkaProducerFactory<String, byte[]> fabrica = new DefaultKafkaProducerFactory<>(
                Map.of(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers()),
                new StringSerializer(), new ByteArraySerializer());
        try {
            new KafkaTemplate<>(fabrica)
                    .send(KafkaTopicConfig.PEDIDOS_CRIADOS, chave, texto.getBytes(StandardCharsets.UTF_8))
                    .get(10, TimeUnit.SECONDS);
        } finally {
            fabrica.destroy();
        }
    }

    private List<MensagemFalha> falhasDe(String chave) {
        return falhaRepository.findAll().stream()
                .filter(f -> chave.equals(f.chave()))
                .toList();
    }
}