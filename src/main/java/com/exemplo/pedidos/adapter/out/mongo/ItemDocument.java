package com.exemplo.pedidos.adapter.out.mongo;

import java.math.BigDecimal;

import com.exemplo.pedidos.domain.ItemPedido;

record ItemDocument(String produto, int quantidade, BigDecimal precoUnitario) {

    static ItemDocument de(ItemPedido item) {
        return new ItemDocument(item.produto(), item.quantidade(), item.precoUnitario());
    }

    ItemPedido paraDominio() {
        return new ItemPedido(produto, quantidade, precoUnitario);
    }
}
