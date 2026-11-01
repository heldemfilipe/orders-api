package com.exemplo.pedidos.cloud;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.testcontainers.mongodb.MongoDBContainer;

import com.exemplo.pedidos.application.port.out.FretePort;

// sem container de Kafka: se alguma classe do perfil cloud ainda precisasse do broker, o contexto nao subiria
// os testes desligam a exportacao de metricas por padrao; aqui ela e religada para provar o /actuator/prometheus
@SpringBootTest(properties = {
        "MONGODB_URI=mongodb://substituida-pelo-testcontainers:27017/pedidos",
        "management.defaults.metrics.export.enabled=true",
        "management.prometheus.metrics.export.enabled=true"
})
@AutoConfigureMockMvc
@ActiveProfiles("cloud")
@Import(PerfilCloudIntegracaoTest.SoMongo.class)
class PerfilCloudIntegracaoTest {

    @TestConfiguration(proxyBeanMethods = false)
    static class SoMongo {

        @Bean
        @ServiceConnection
        MongoDBContainer mongoDbContainer() {
            return new MongoDBContainer("mongo:7.0");
        }
    }

    @Autowired
    ApplicationContext contexto;

    @Autowired
    MockMvcTester mvc;

    @Test
    void contextoSobeSemNadaDeKafkaNemDeFrete() {
        assertThat(contexto.getBeanNamesForType(KafkaTemplate.class)).isEmpty();
        assertThat(contexto.getBeanNamesForType(KafkaListenerEndpointRegistry.class)).isEmpty();
        assertThat(contexto.getBeanNamesForType(FretePort.class)).isEmpty();
        assertThat(contexto.containsBean("pedidoCriadoPersistenciaListener")).isFalse();
        assertThat(contexto.containsBean("pedidoCriadoAuditoriaListener")).isFalse();
        assertThat(contexto.containsBean("deadLetterListener")).isFalse();
    }

    @Test
    void crudFuncionaSoComOMongo() {
        var resposta = mvc.post().uri("/pedidos")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"clienteId":"cli-cloud","itens":[{"produto":"caneta","quantidade":2,"precoUnitario":3.50}]}
                        """)
                .exchange();
        assertThat(resposta).hasStatus(HttpStatus.CREATED);
        String local = resposta.getResponse().getHeader("Location");

        assertThat(mvc.get().uri(local))
                .hasStatusOk()
                .bodyJson().extractingPath("$.total").isEqualTo(7.00);
    }

    @Test
    void endpointDeEventosNaoExisteNaNuvem() {
        assertThat(mvc.post().uri("/pedidos/eventos")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"clienteId":"cli-cloud","itens":[{"produto":"caneta","quantidade":2,"precoUnitario":3.50}]}
                        """))
                .hasStatus(HttpStatus.METHOD_NOT_ALLOWED);
    }

    @Test
    void healthSobeEOQueNaoEDaNuvemNaoEstaExposto() {
        assertThat(mvc.get().uri("/actuator/health"))
                .hasStatusOk()
                .bodyJson().extractingPath("$.status").isEqualTo("UP");
        assertThat(mvc.get().uri("/actuator/circuitbreakers")).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(mvc.get().uri("/actuator/env")).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void probesDeLivenessEReadinessParaOKubernetes() {
        assertThat(mvc.get().uri("/actuator/health/liveness"))
                .hasStatusOk()
                .bodyJson().extractingPath("$.status").isEqualTo("UP");
        assertThat(mvc.get().uri("/actuator/health/readiness"))
                .hasStatusOk()
                .bodyJson().extractingPath("$.status").isEqualTo("UP");
    }

    @Test
    void metricasNoFormatoDoPrometheus() {
        assertThat(mvc.get().uri("/actuator/prometheus"))
                .hasStatusOk()
                .bodyText().contains("jvm_memory_used_bytes");
    }

    @Test
    void documentacaoDaApiNaoFicaPublicadaNaNuvem() {
        assertThat(mvc.get().uri("/v3/api-docs")).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(mvc.get().uri("/swagger-ui.html")).hasStatus(HttpStatus.NOT_FOUND);
    }
}
