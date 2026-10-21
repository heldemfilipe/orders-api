package com.exemplo.pedidos.pedido.dto;

import java.math.BigDecimal;

public record ItemRequest(String produto, int quantidade, BigDecimal precoUnitario) {
}
