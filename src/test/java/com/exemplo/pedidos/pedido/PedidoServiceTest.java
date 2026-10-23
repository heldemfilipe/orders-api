package com.exemplo.pedidos.pedido;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import com.exemplo.pedidos.erro.PedidoNaoEncontradoException;
import com.exemplo.pedidos.erro.TransicaoStatusInvalidaException;
import com.exemplo.pedidos.pedido.dto.AtualizarPedidoRequest;
import com.exemplo.pedidos.pedido.dto.ItemRequest;
import com.exemplo.pedidos.pedido.dto.PedidoRequest;
import com.exemplo.pedidos.pedido.dto.PedidoResponse;

@ExtendWith(MockitoExtension.class)
class PedidoServiceTest {

    @Mock
    PedidoRepository repository;

    PedidoService service;

    @BeforeEach
    void setUp() {
        service = new PedidoService(repository);
    }
    
    @Test
    void criarCalculaTotalNoServidor() {
        when(repository.save(any(Pedido.class))).thenAnswer(inv -> inv.getArgument(0));
        PedidoRequest request = new PedidoRequest("cli-1", List.of(
                new ItemRequest("caneta", 2, new BigDecimal("3.50")),
                new ItemRequest("caderno", 1, new BigDecimal("12.90"))));

        PedidoResponse resposta = service.criar(request);

        assertThat(resposta.total()).isEqualByComparingTo("19.90");
        assertThat(resposta.status()).isEqualTo(StatusPedido.CRIADO);
    }

    @Test
    void buscarInexistenteLancaExcecao() {
        when(repository.findById("x")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.buscar("x"))
                .isInstanceOf(PedidoNaoEncontradoException.class)
                .hasMessageContaining("x");
    }

    @Test
    void removerInexistenteNaoApaga() {
        when(repository.existsById("x")).thenReturn(false);

        assertThatThrownBy(() -> service.remover("x"))
                .isInstanceOf(PedidoNaoEncontradoException.class);

        verify(repository, never()).deleteById(any());
    }

    @Test
    void atualizarPermitidoGravaStatusNovoERecalculaTotal() {
        when(repository.findById("p1")).thenReturn(Optional.of(pedidoCom(StatusPedido.CRIADO)));
        when(repository.save(any(Pedido.class))).thenAnswer(inv -> inv.getArgument(0));
        AtualizarPedidoRequest request = new AtualizarPedidoRequest(
                List.of(new ItemRequest("caneta", 10, new BigDecimal("3.50"))), StatusPedido.PAGO);

        PedidoResponse resposta = service.atualizar("p1", request);

        assertThat(resposta.status()).isEqualTo(StatusPedido.PAGO);
        assertThat(resposta.total()).isEqualByComparingTo("35.00");
    }

    @Test
    void atualizarCanceladoParaPagoLancaErroENaoGrava() {
        when(repository.findById("p1")).thenReturn(Optional.of(pedidoCom(StatusPedido.CANCELADO)));
        AtualizarPedidoRequest request = new AtualizarPedidoRequest(
                List.of(new ItemRequest("caneta", 2, new BigDecimal("3.50"))), StatusPedido.PAGO);

        assertThatThrownBy(() -> service.atualizar("p1", request))
                .isInstanceOf(TransicaoStatusInvalidaException.class)
                .hasMessageContaining("CANCELADO -> PAGO");

        verify(repository, never()).save(any());
    }

    private Pedido pedidoCom(StatusPedido status) {
        return new Pedido("p1", "cli-1",
                List.of(new ItemPedido("caneta", 2, new BigDecimal("3.50"))),
                new BigDecimal("7.00"), status, Instant.now());
    }
}