package com.exemplo.pedidos.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

// o fato "um pedido foi criado", na linguagem do negocio. O formato que viaja no Kafka
// (adapter.kafka.PedidoCriado) e outro tipo, que pode mudar sem tocar aqui.
public record PedidoCriadoEvento(
        UUID eventId,
        UUID pedidoId,
        String clienteId,
        List<ItemPedido> itens,
        BigDecimal total,
        Instant ocorridoEm) {
}
