package com.exemplo.pedidos.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.exemplo.pedidos.application.erro.EventoInvalidoException;
import com.exemplo.pedidos.application.fake.PedidoRepositoryEmMemoria;
import com.exemplo.pedidos.application.port.in.RegistrarPedidoCriadoUseCase.Resultado;
import com.exemplo.pedidos.application.port.out.FretePort;
import com.exemplo.pedidos.domain.Frete;
import com.exemplo.pedidos.domain.ItemPedido;
import com.exemplo.pedidos.domain.Pedido;
import com.exemplo.pedidos.domain.PedidoCriadoEvento;
import com.exemplo.pedidos.domain.StatusPedido;

class RegistrarPedidoCriadoServiceTest {

    PedidoRepositoryEmMemoria repository = new PedidoRepositoryEmMemoria();
    FretePort frete = clienteId -> new Frete(new BigDecimal("12.50"), 3, false);

    @Test
    void gravaPedidoComIdDoEventoStatusCriadoEFrete() {
        RegistrarPedidoCriadoService service = new RegistrarPedidoCriadoService(repository, frete);
        PedidoCriadoEvento evento = evento(new BigDecimal("7.00"));

        Resultado resultado = service.registrar(evento);

        assertThat(resultado).isEqualTo(Resultado.GRAVADO);
        Pedido pedido = repository.buscarPorId(evento.pedidoId().toString()).orElseThrow();
        assertThat(pedido.getClienteId()).isEqualTo("cli-1");
        assertThat(pedido.getStatus()).isEqualTo(StatusPedido.CRIADO);
        assertThat(pedido.getTotal()).isEqualByComparingTo("7.00");
        assertThat(pedido.getCriadoEm()).isEqualTo(Instant.parse("2026-10-28T15:00:00Z"));
        assertThat(pedido.getItens()).hasSize(1);
        assertThat(pedido.getFrete()).isEqualByComparingTo("12.50");
        assertThat(pedido.isFreteEstimado()).isFalse();
    }

    @Test
    void eventoReentregueEIgnoradoENaoDesfazAlteracaoDoPedido() {
        RegistrarPedidoCriadoService service = new RegistrarPedidoCriadoService(repository, frete);
        PedidoCriadoEvento evento = evento(new BigDecimal("7.00"));
        service.registrar(evento);
        repository.buscarPorId(evento.pedidoId().toString()).orElseThrow()
                .atualizar(evento.itens(), StatusPedido.PAGO);

        Resultado resultado = service.registrar(evento);

        assertThat(resultado).isEqualTo(Resultado.JA_EXISTIA);
        assertThat(repository.todos()).hasSize(1);
        assertThat(repository.todos().get(0).getStatus()).isEqualTo(StatusPedido.PAGO);
    }

    @Test
    void totalNegativoLancaEventoInvalidoSemGravarNemCotarFrete() {
        FretePort proibido = clienteId -> {
            throw new AssertionError("nao devia cotar o frete de um evento invalido");
        };
        RegistrarPedidoCriadoService service = new RegistrarPedidoCriadoService(repository, proibido);

        assertThatThrownBy(() -> service.registrar(evento(new BigDecimal("-1.00"))))
                .isInstanceOf(EventoInvalidoException.class)
                .hasMessageContaining("total negativo");

        assertThat(repository.todos()).isEmpty();
    }

    @Test
    void freteEstimadoDoFallbackFicaMarcadoNoPedido() {
        FretePort padrao = clienteId -> Frete.PADRAO;
        RegistrarPedidoCriadoService service = new RegistrarPedidoCriadoService(repository, padrao);
        PedidoCriadoEvento evento = evento(new BigDecimal("7.00"));

        service.registrar(evento);

        Pedido pedido = repository.buscarPorId(evento.pedidoId().toString()).orElseThrow();
        assertThat(pedido.getFrete()).isEqualByComparingTo("20.00");
        assertThat(pedido.isFreteEstimado()).isTrue();
    }

    private static PedidoCriadoEvento evento(BigDecimal total) {
        return new PedidoCriadoEvento(UUID.randomUUID(), UUID.randomUUID(), "cli-1",
                List.of(new ItemPedido("caneta", 2, new BigDecimal("3.50"))),
                total, Instant.parse("2026-10-28T15:00:00Z"));
    }
}
