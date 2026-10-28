package com.exemplo.pedidos.adapter.out.mongo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import org.bson.Document;
import org.bson.types.Decimal128;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.MongoTemplate;

import com.exemplo.pedidos.TestcontainersConfiguration;
import com.exemplo.pedidos.application.erro.PedidoJaExisteException;
import com.exemplo.pedidos.application.port.out.PedidoRepositoryPort;
import com.exemplo.pedidos.domain.ItemPedido;
import com.exemplo.pedidos.domain.Pedido;
import com.exemplo.pedidos.domain.StatusPedido;

// mesmas anotacoes dos outros testes de integracao: o contexto e os containers sao reaproveitados
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class PedidoMongoAdapterIntegracaoTest {

    @Autowired
    PedidoRepositoryPort port;

    @Autowired
    MongoTemplate mongo;

    @Test
    void salvarAtribuiOIdEBuscarDevolveOPedidoCompleto() {
        Pedido novo = Pedido.novo("cli-adapter", List.of(new ItemPedido("caneta", 2, new BigDecimal("3.50"))));
        novo.definirFrete(new BigDecimal("12.50"), true);

        Pedido salvo = port.salvar(novo);

        assertThat(salvo.getId()).isNotBlank();
        Pedido lido = port.buscarPorId(salvo.getId()).orElseThrow();
        assertThat(lido.getClienteId()).isEqualTo("cli-adapter");
        assertThat(lido.getTotal()).isEqualByComparingTo("7.00");
        assertThat(lido.getStatus()).isEqualTo(StatusPedido.CRIADO);
        assertThat(lido.getItens()).containsExactly(new ItemPedido("caneta", 2, new BigDecimal("3.50")));
        assertThat(lido.getFrete()).isEqualByComparingTo("12.50");
        assertThat(lido.isFreteEstimado()).isTrue();
    }

    @Test
    void salvarDeNovoSubstituiOMesmoDocumento() {
        Pedido pedido = port.salvar(Pedido.novo("cli-adapter", List.of(new ItemPedido("caneta", 1, BigDecimal.ONE))));
        pedido.atualizar(pedido.getItens(), StatusPedido.PAGO);

        port.salvar(pedido);

        assertThat(port.buscarPorId(pedido.getId()).orElseThrow().getStatus()).isEqualTo(StatusPedido.PAGO);
    }

    @Test
    void inserirComIdOcupadoLancaPedidoJaExiste() {
        String id = UUID.randomUUID().toString();
        Pedido pedido = new Pedido(id, "cli-adapter", List.of(new ItemPedido("caneta", 1, BigDecimal.ONE)),
                BigDecimal.ONE, StatusPedido.CRIADO, Instant.now());
        port.inserir(pedido);

        assertThatThrownBy(() -> port.inserir(pedido)).isInstanceOf(PedidoJaExisteException.class);
    }

    @Test
    void documentoNovoGravaOAliasNoLugarDoNomeDaClasse() {
        Pedido salvo = port.salvar(Pedido.novo("cli-alias", List.of(new ItemPedido("caneta", 1, BigDecimal.ONE))));

        Document cru = mongo.getCollection("pedidos").find(new Document("clienteId", "cli-alias")).first();

        assertThat(cru).isNotNull();
        assertThat(cru.get("_class")).isEqualTo("pedido");
        assertThat(cru.get("_id").toString()).isEqualTo(salvo.getId());
    }

    // documentos gravados antes da refatoracao tem _class com um nome de classe que nao existe mais
    @Test
    void documentoAntigoComNomeDeClasseExtintoAindaELido() {
        String id = "legado-" + UUID.randomUUID();
        mongo.getCollection("pedidos").insertOne(new Document("_id", id)
                .append("_class", "com.exemplo.pedidos.pedido.Pedido")
                .append("clienteId", "cli-legado")
                .append("itens", List.of(new Document("produto", "caneta").append("quantidade", 2)
                        .append("precoUnitario", new Decimal128(new BigDecimal("3.50")))))
                .append("total", new Decimal128(new BigDecimal("7.00")))
                .append("status", "PAGO")
                .append("criadoEm", Date.from(Instant.parse("2026-10-01T12:00:00Z")))
                .append("frete", new Decimal128(new BigDecimal("20.00")))
                .append("freteEstimado", true));

        Pedido lido = port.buscarPorId(id).orElseThrow();

        assertThat(lido.getClienteId()).isEqualTo("cli-legado");
        assertThat(lido.getStatus()).isEqualTo(StatusPedido.PAGO);
        assertThat(lido.getTotal()).isEqualByComparingTo("7.00");
        assertThat(lido.getItens()).containsExactly(new ItemPedido("caneta", 2, new BigDecimal("3.50")));
        assertThat(lido.getCriadoEm()).isEqualTo(Instant.parse("2026-10-01T12:00:00Z"));
        assertThat(lido.isFreteEstimado()).isTrue();
    }
}
