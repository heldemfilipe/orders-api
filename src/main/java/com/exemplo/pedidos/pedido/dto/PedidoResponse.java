package com.exemplo.pedidos.pedido.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.exemplo.pedidos.pedido.Pedido;
import com.exemplo.pedidos.pedido.StatusPedido;

public record PedidoResponse(String id, String clienteId, List<ItemResponse> itens,
                             BigDecimal total, StatusPedido status, Instant criadoEm,
                             BigDecimal frete, boolean freteEstimado) {

    public static PedidoResponse de(Pedido pedido) {
        return new PedidoResponse(
                pedido.getId(),
                pedido.getClienteId(),
                pedido.getItens().stream().map(ItemResponse::de).toList(),
                pedido.getTotal(),
                pedido.getStatus(),
                pedido.getCriadoEm(),
                pedido.getFrete(),
                pedido.isFreteEstimado());
    }
}