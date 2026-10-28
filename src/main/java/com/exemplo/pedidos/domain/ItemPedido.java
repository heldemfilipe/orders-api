package com.exemplo.pedidos.domain;

import java.math.BigDecimal;

public record ItemPedido(String produto, int quantidade, BigDecimal precoUnitario) {
}
