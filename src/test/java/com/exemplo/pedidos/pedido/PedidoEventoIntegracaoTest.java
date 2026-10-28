package com.exemplo.pedidos.pedido;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.testcontainers.kafka.KafkaContainer;

import com.exemplo.pedidos.TestcontainersConfiguration;
import com.exemplo.pedidos.adapter.kafka.KafkaTopicConfig;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class PedidoEventoIntegracaoTest {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    KafkaContainer kafka;

    @Test
    void postPublicaEventoNoTopicoComChaveDoCliente() {
        postar("cli-evt");

        List<ConsumerRecord<String, String>> registros = lerRegistros("cli-evt", 1);

        ConsumerRecord<String, String> registro = registros.get(0);
        assertThat(registro.key()).isEqualTo("cli-evt");
        assertThat(registro.headers().toArray()).isEmpty();
        assertThat(registro.value())
                .contains("\"clienteId\":\"cli-evt\"")
                .contains("\"total\":7.00");
    }

    @Test
    void mesmoClienteVaiSempreParaAMesmaParticao() {
        postar("cli-ordem");
        postar("cli-ordem");
        postar("cli-ordem");

        List<ConsumerRecord<String, String>> registros = lerRegistros("cli-ordem", 3);

        assertThat(registros).extracting(ConsumerRecord::partition).containsOnly(registros.get(0).partition());
    }

    private void postar(String clienteId) {
        assertThat(mvc.post().uri("/pedidos/eventos")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"clienteId":"%s","itens":[{"produto":"caneta","quantidade":2,"precoUnitario":3.50}]}
                        """.formatted(clienteId)))
                .hasStatus(HttpStatus.ACCEPTED);
    }

    private List<ConsumerRecord<String, String>> lerRegistros(String chave, int quantidade) {
        Map<String, Object> config = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, UUID.randomUUID().toString(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);

        List<ConsumerRecord<String, String>> encontrados = new ArrayList<>();
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(config)) {
            consumer.subscribe(List.of(KafkaTopicConfig.PEDIDOS_CRIADOS));
            long limite = System.currentTimeMillis() + 20_000;
            while (encontrados.size() < quantidade && System.currentTimeMillis() < limite) {
                for (ConsumerRecord<String, String> registro : consumer.poll(Duration.ofMillis(500))) {
                    if (chave.equals(registro.key())) {
                        encontrados.add(registro);
                    }
                }
            }
        }
        assertThat(encontrados).as("registros com a chave %s no topico", chave).hasSize(quantidade);
        return encontrados;
    }
}