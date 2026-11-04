package com.exemplo.pedidos.cloud;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

// Sem container de proposito: a porta 1 nao tem ninguem escutando, como um Mongo fora do ar.
// Antes do timeout curto, cada requisicao ficava presa 30 s (o padrao do driver) e terminava em 500.
@SpringBootTest(properties = "MONGODB_URI=mongodb://localhost:1/pedidos")
@AutoConfigureMockMvc
@ActiveProfiles("cloud")
class MongoIndisponivelIntegracaoTest {

    private static final Duration LIMITE = Duration.ofSeconds(8);
    // o driver espera os 3 s configurados antes de desistir; falhar na hora seria outro defeito
    private static final Duration MINIMO = Duration.ofSeconds(2);

    @Autowired
    MockMvcTester mvc;

    @Test
    void listarPedidosSemBancoDevolve503Rapido() {
        long inicio = System.nanoTime();
        var resposta = mvc.get().uri("/pedidos").exchange();
        Duration gasto = Duration.ofNanos(System.nanoTime() - inicio);

        assertThat(resposta).hasStatus(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(resposta).bodyJson().extractingPath("$.title").isEqualTo("Banco indisponível");
        assertThat(gasto).isBetween(MINIMO, LIMITE);
    }

    @Test
    void criarPedidoSemBancoDevolve503Rapido() {
        long inicio = System.nanoTime();
        var resposta = mvc.post().uri("/pedidos")
                .contentType("application/json")
                .content("""
                        {"clienteId":"cli-1","itens":[{"produto":"caneta","quantidade":2,"precoUnitario":3.50}]}
                        """)
                .exchange();
        Duration gasto = Duration.ofNanos(System.nanoTime() - inicio);

        assertThat(resposta).hasStatus(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(gasto).isLessThan(LIMITE);
    }

    @Test
    void healthGeralFicaDownRapidoEAsProbesNaoDependemDoBanco() {
        long inicio = System.nanoTime();
        var health = mvc.get().uri("/actuator/health").exchange();
        Duration gasto = Duration.ofNanos(System.nanoTime() - inicio);

        assertThat(health).hasStatus(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(gasto).isLessThan(LIMITE);

        // decisao de projeto: o pod continua Ready sem o banco (o Kubernetes nao deve reiniciar por isso)
        assertThat(mvc.get().uri("/actuator/health/liveness")).hasStatusOk();
        assertThat(mvc.get().uri("/actuator/health/readiness")).hasStatusOk();
    }
}
