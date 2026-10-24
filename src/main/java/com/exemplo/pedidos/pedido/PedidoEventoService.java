package com.exemplo.pedidos.pedido;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.exemplo.pedidos.kafka.PedidoCriado;
import com.exemplo.pedidos.kafka.PedidoEventoPublisher;
import com.exemplo.pedidos.pedido.dto.ItemRequest;
import com.exemplo.pedidos.pedido.dto.PedidoAceito;
import com.exemplo.pedidos.pedido.dto.PedidoRequest;

@Service
public class PedidoEventoService {

    private final PedidoEventoPublisher publisher;

    public PedidoEventoService(PedidoEventoPublisher publisher) {
        this.publisher = publisher;
    }

    public PedidoAceito aceitar(PedidoRequest request) {
        List<ItemPedido> itens = request.itens().stream().map(ItemRequest::paraItem).toList();
        PedidoCriado evento = new PedidoCriado(
                UUID.randomUUID(),
                UUID.randomUUID(),
                request.clienteId(),
                itens.stream()
                        .map(i -> new PedidoCriado.Item(i.produto(), i.quantidade(), i.precoUnitario()))
                        .toList(),
                Pedido.calcularTotal(itens),
                Instant.now());
        publisher.publicar(evento);
        return new PedidoAceito(evento.pedidoId(), evento.eventId());
    }
}