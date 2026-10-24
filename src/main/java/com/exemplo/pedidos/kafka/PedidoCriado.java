package com.exemplo.pedidos.kafka;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PedidoCriado(
        UUID eventId,
        UUID pedidoId,
        String clienteId,
        List<Item> itens,
        BigDecimal total,
        Instant ocorridoEm) {

    public record Item(String produto, int quantidade, BigDecimal precoUnitario) {
    }
}