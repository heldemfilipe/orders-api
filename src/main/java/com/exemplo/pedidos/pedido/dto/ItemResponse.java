package com.exemplo.pedidos.pedido.dto;

import java.math.BigDecimal;

import com.exemplo.pedidos.pedido.ItemPedido;

public record ItemResponse(String produto, int quantidade, BigDecimal precoUnitario) {

    public static ItemResponse de(ItemPedido item) {
        return new ItemResponse(item.produto(), item.quantidade(), item.precoUnitario());
    }
}
