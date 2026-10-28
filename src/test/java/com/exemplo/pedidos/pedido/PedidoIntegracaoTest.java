package com.exemplo.pedidos.pedido;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import com.exemplo.pedidos.TestcontainersConfiguration;
import com.exemplo.pedidos.application.Paginacao;
import com.exemplo.pedidos.application.port.out.PedidoRepositoryPort;
import com.exemplo.pedidos.domain.ItemPedido;
import com.exemplo.pedidos.domain.Pedido;
import com.exemplo.pedidos.domain.StatusPedido;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class PedidoIntegracaoTest {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    PedidoRepositoryPort repository;

    @Autowired
    MongoTemplate mongo;

    @BeforeEach
    void limparBanco() {
        mongo.remove(new Query(), "pedidos");
    }

        @Test
    void postGravaPedidoNoMongoComTotalCalculado() {
        assertThat(mvc.post().uri("/pedidos")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"clienteId":"cli-1","itens":[{"produto":"caneta","quantidade":2,"precoUnitario":3.50}]}
                        """))
                .hasStatus(HttpStatus.CREATED)
                .headers().containsHeader("Location");

        List<Pedido> gravados = todos().stream()
                .filter(p -> "cli-1".equals(p.getClienteId()))
                .toList();
        assertThat(gravados).hasSize(1);
        assertThat(gravados.get(0).getTotal()).isEqualByComparingTo("7.00");
        assertThat(gravados.get(0).getStatus()).isEqualTo(StatusPedido.CRIADO);
    }

    @Test
    void postInvalidoDevolve400ENaoGrava() {
        assertThat(mvc.post().uri("/pedidos")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"clienteId\":\"\",\"itens\":[]}"))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON);

        assertThat(todos()).noneMatch(p -> p.getClienteId().isBlank());
    }

    @Test
    void getInexistenteDevolve404() {
        assertThat(mvc.get().uri("/pedidos/000000000000000000000000"))
                .hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void putDeCanceladoParaPagoDevolve409EMantemStatus() {
        Pedido cancelado = repository.salvar(new Pedido(null, "cli-1",
                List.of(new ItemPedido("caneta", 2, new BigDecimal("3.50"))),
                new BigDecimal("7.00"), StatusPedido.CANCELADO, Instant.now()));

        assertThat(mvc.put().uri("/pedidos/" + cancelado.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"itens":[{"produto":"caneta","quantidade":2,"precoUnitario":3.50}],"status":"PAGO"}
                        """))
                .hasStatus(HttpStatus.CONFLICT);

        assertThat(repository.buscarPorId(cancelado.getId()).orElseThrow().getStatus())
                .isEqualTo(StatusPedido.CANCELADO);
    }

    private List<Pedido> todos() {
        return repository.listar(new Paginacao(0, 1000, List.of())).conteudo();
    }
}
