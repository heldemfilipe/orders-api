package com.exemplo.pedidos.pedido.dto;

import java.util.List;

import com.exemplo.pedidos.pedido.StatusPedido;

public record AtualizarPedidoRequest(List<ItemRequest> itens, StatusPedido status) {
}
