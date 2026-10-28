package com.exemplo.pedidos.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.exemplo.pedidos.application.erro.EventoNaoPublicadoException;
import com.exemplo.pedidos.application.port.in.AceitarPedidoUseCase;
import com.exemplo.pedidos.application.port.out.EventoPublisherPort;
import com.exemplo.pedidos.domain.ItemPedido;
import com.exemplo.pedidos.domain.PedidoCriadoEvento;

class PedidoEventoServiceTest {

    @Test
    void aceitarPublicaEventoComTotalCalculadoEDevolveOsIds() {
        List<PedidoCriadoEvento> publicados = new ArrayList<>();
        PedidoEventoService service = new PedidoEventoService(publicados::add);

        AceitarPedidoUseCase.Aceito aceito = service.aceitar(new AceitarPedidoUseCase.Comando("cli-1", List.of(
                new ItemPedido("caneta", 2, new BigDecimal("3.50")),
                new ItemPedido("caderno", 1, new BigDecimal("12.90")))));

        assertThat(publicados).hasSize(1);
        PedidoCriadoEvento evento = publicados.get(0);
        assertThat(evento.clienteId()).isEqualTo("cli-1");
        assertThat(evento.total()).isEqualByComparingTo("19.90");
        assertThat(evento.itens()).hasSize(2);
        assertThat(evento.eventId()).isNotEqualTo(evento.pedidoId());
        assertThat(aceito.eventId()).isEqualTo(evento.eventId());
        assertThat(aceito.pedidoId()).isEqualTo(evento.pedidoId());
    }

    @Test
    void falhaNoPublisherPropagaEAindaNaoDevolveIds() {
        EventoPublisherPort quebrado = evento -> {
            throw new EventoNaoPublicadoException(UUID.randomUUID(), new RuntimeException("broker fora"));
        };
        PedidoEventoService service = new PedidoEventoService(quebrado);

        assertThatThrownBy(() -> service.aceitar(new AceitarPedidoUseCase.Comando("cli-1",
                List.of(new ItemPedido("caneta", 2, new BigDecimal("3.50"))))))
                .isInstanceOf(EventoNaoPublicadoException.class);
    }
}
