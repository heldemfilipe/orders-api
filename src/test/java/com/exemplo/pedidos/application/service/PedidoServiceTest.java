package com.exemplo.pedidos.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.exemplo.pedidos.application.Paginacao;
import com.exemplo.pedidos.application.erro.PedidoNaoEncontradoException;
import com.exemplo.pedidos.application.fake.PedidoRepositoryEmMemoria;
import com.exemplo.pedidos.application.port.in.AtualizarPedidoUseCase;
import com.exemplo.pedidos.application.port.in.CriarPedidoUseCase;
import com.exemplo.pedidos.domain.ItemPedido;
import com.exemplo.pedidos.domain.Pedido;
import com.exemplo.pedidos.domain.StatusPedido;
import com.exemplo.pedidos.domain.TransicaoStatusInvalidaException;

// sem Spring e sem Mockito: o repositorio e um mapa em memoria
class PedidoServiceTest {

    PedidoRepositoryEmMemoria repository;
    PedidoService service;

    @BeforeEach
    void setUp() {
        repository = new PedidoRepositoryEmMemoria();
        service = new PedidoService(repository);
    }

    @Test
    void criarCalculaTotalNoServidorEGravaComId() {
        Pedido criado = service.criar(new CriarPedidoUseCase.Comando("cli-1", List.of(
                new ItemPedido("caneta", 2, new BigDecimal("3.50")),
                new ItemPedido("caderno", 1, new BigDecimal("12.90")))));

        assertThat(criado.getTotal()).isEqualByComparingTo("19.90");
        assertThat(criado.getStatus()).isEqualTo(StatusPedido.CRIADO);
        assertThat(criado.getId()).isNotNull();
        assertThat(repository.buscarPorId(criado.getId())).isPresent();
    }

    @Test
    void buscarInexistenteLancaExcecao() {
        assertThatThrownBy(() -> service.buscar("x"))
                .isInstanceOf(PedidoNaoEncontradoException.class)
                .hasMessageContaining("x");
    }

    @Test
    void removerInexistenteLancaExcecao() {
        assertThatThrownBy(() -> service.remover("x"))
                .isInstanceOf(PedidoNaoEncontradoException.class);
    }

    @Test
    void removerApagaOPedido() {
        Pedido criado = service.criar(comandoDeCriacao());

        service.remover(criado.getId());

        assertThat(repository.existe(criado.getId())).isFalse();
    }

    @Test
    void atualizarPermitidoGravaStatusNovoERecalculaTotal() {
        Pedido criado = service.criar(comandoDeCriacao());

        Pedido atualizado = service.atualizar(new AtualizarPedidoUseCase.Comando(criado.getId(),
                List.of(new ItemPedido("caneta", 10, new BigDecimal("3.50"))), StatusPedido.PAGO));

        assertThat(atualizado.getStatus()).isEqualTo(StatusPedido.PAGO);
        assertThat(atualizado.getTotal()).isEqualByComparingTo("35.00");
        assertThat(repository.buscarPorId(criado.getId()).orElseThrow().getStatus()).isEqualTo(StatusPedido.PAGO);
    }

    @Test
    void atualizarCanceladoParaPagoLancaErroENaoAlteraOPedido() {
        Pedido criado = service.criar(comandoDeCriacao());
        service.atualizar(new AtualizarPedidoUseCase.Comando(criado.getId(), criado.getItens(), StatusPedido.CANCELADO));

        assertThatThrownBy(() -> service.atualizar(new AtualizarPedidoUseCase.Comando(
                criado.getId(), List.of(new ItemPedido("caneta", 99, new BigDecimal("1.00"))), StatusPedido.PAGO)))
                .isInstanceOf(TransicaoStatusInvalidaException.class)
                .hasMessageContaining("CANCELADO -> PAGO");

        Pedido gravado = repository.buscarPorId(criado.getId()).orElseThrow();
        assertThat(gravado.getStatus()).isEqualTo(StatusPedido.CANCELADO);
        assertThat(gravado.getTotal()).isEqualByComparingTo("7.00");
    }

    @Test
    void listarDevolveApenasAPaginaPedida() {
        for (int i = 0; i < 5; i++) {
            service.criar(comandoDeCriacao());
        }

        var pagina = service.listar(new Paginacao(1, 2, List.of()));

        assertThat(pagina.conteudo()).hasSize(2);
        assertThat(pagina.totalElementos()).isEqualTo(5);
        assertThat(pagina.numero()).isEqualTo(1);
    }

    private static CriarPedidoUseCase.Comando comandoDeCriacao() {
        return new CriarPedidoUseCase.Comando("cli-1", List.of(new ItemPedido("caneta", 2, new BigDecimal("3.50"))));
    }
}
