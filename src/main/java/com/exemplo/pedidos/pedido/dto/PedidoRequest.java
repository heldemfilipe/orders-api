package com.exemplo.pedidos.pedido.dto;

import java.util.List;

public record PedidoRequest(String clienteId, List<ItemRequest> itens) {
}
