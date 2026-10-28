package com.exemplo.pedidos.application.service;

import java.time.Instant;
import java.util.UUID;

import com.exemplo.pedidos.application.port.in.AceitarPedidoUseCase;
import com.exemplo.pedidos.application.port.out.EventoPublisherPort;
import com.exemplo.pedidos.domain.Pedido;
import com.exemplo.pedidos.domain.PedidoCriadoEvento;

public class PedidoEventoService implements AceitarPedidoUseCase {

    private final EventoPublisherPort publisher;

    public PedidoEventoService(EventoPublisherPort publisher) {
        this.publisher = publisher;
    }

    @Override
    public Aceito aceitar(Comando comando) {
        PedidoCriadoEvento evento = new PedidoCriadoEvento(
                UUID.randomUUID(),
                UUID.randomUUID(),
                comando.clienteId(),
                comando.itens(),
                Pedido.calcularTotal(comando.itens()),
                Instant.now());
        publisher.publicar(evento);
        return new Aceito(evento.pedidoId(), evento.eventId());
    }
}
