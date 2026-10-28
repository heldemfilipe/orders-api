package com.exemplo.pedidos.adapter.kafka;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.exemplo.pedidos.domain.ItemPedido;
import com.exemplo.pedidos.domain.PedidoCriadoEvento;

// contrato do evento no tópico: é o que trafega no Kafka, separado do evento de domínio
public record PedidoCriado(
        UUID eventId,
        UUID pedidoId,
        String clienteId,
        List<Item> itens,
        BigDecimal total,
        Instant ocorridoEm) {

    public record Item(String produto, int quantidade, BigDecimal precoUnitario) {
    }

    public static PedidoCriado de(PedidoCriadoEvento evento) {
        return new PedidoCriado(evento.eventId(), evento.pedidoId(), evento.clienteId(),
                evento.itens().stream()
                        .map(i -> new Item(i.produto(), i.quantidade(), i.precoUnitario()))
                        .toList(),
                evento.total(), evento.ocorridoEm());
    }

    public PedidoCriadoEvento paraEvento() {
        return new PedidoCriadoEvento(eventId, pedidoId, clienteId,
                itens.stream()
                        .map(i -> new ItemPedido(i.produto(), i.quantidade(), i.precoUnitario()))
                        .toList(),
                total, ocorridoEm);
    }
}
