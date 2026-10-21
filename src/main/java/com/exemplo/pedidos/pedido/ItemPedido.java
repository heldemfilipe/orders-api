package com.exemplo.pedidos.pedido;

import java.math.BigDecimal;

public record ItemPedido(String produto, int quantidade, BigDecimal precoUnitario) {
}
