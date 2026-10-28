package com.exemplo.pedidos.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

class PedidoTest {

    private static final List<ItemPedido> DOIS_ITENS = List.of(
            new ItemPedido("caneta", 2, new BigDecimal("3.50")),
            new ItemPedido("caderno", 1, new BigDecimal("12.90")));

    @Test
    void novoPedidoNasceCriadoSemIdEComTotalCalculado() {
        Pedido pedido = Pedido.novo("cli-1", DOIS_ITENS);

        assertThat(pedido.getId()).isNull();
        assertThat(pedido.getStatus()).isEqualTo(StatusPedido.CRIADO);
        assertThat(pedido.getTotal()).isEqualByComparingTo("19.90");
        assertThat(pedido.getCriadoEm()).isNotNull();
    }

    @Test
    void atualizarRecalculaOTotalEMudaOStatus() {
        Pedido pedido = Pedido.novo("cli-1", DOIS_ITENS);

        pedido.atualizar(List.of(new ItemPedido("caneta", 10, new BigDecimal("3.50"))), StatusPedido.PAGO);

        assertThat(pedido.getTotal()).isEqualByComparingTo("35.00");
        assertThat(pedido.getStatus()).isEqualTo(StatusPedido.PAGO);
    }

    @Test
    void transicaoProibidaLancaEMantemOEstado() {
        Pedido pedido = Pedido.novo("cli-1", DOIS_ITENS);
        pedido.atualizar(DOIS_ITENS, StatusPedido.CANCELADO);

        assertThatThrownBy(() -> pedido.atualizar(List.of(new ItemPedido("x", 1, BigDecimal.ONE)), StatusPedido.PAGO))
                .isInstanceOf(TransicaoStatusInvalidaException.class)
                .hasMessageContaining("CANCELADO -> PAGO");

        assertThat(pedido.getStatus()).isEqualTo(StatusPedido.CANCELADO);
        assertThat(pedido.getTotal()).isEqualByComparingTo("19.90");
    }

    @Test
    void oIdSoPodeSerAtribuidoUmaVez() {
        Pedido pedido = Pedido.novo("cli-1", DOIS_ITENS);
        pedido.atribuirId("abc");

        assertThatThrownBy(() -> pedido.atribuirId("outro")).isInstanceOf(IllegalStateException.class);
        assertThat(pedido.getId()).isEqualTo("abc");
    }

    @Test
    void aListaDeItensNaoPodeSerAlteradaPorFora() {
        Pedido pedido = Pedido.novo("cli-1", DOIS_ITENS);

        assertThatThrownBy(() -> pedido.getItens().clear()).isInstanceOf(UnsupportedOperationException.class);
    }
}
