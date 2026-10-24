package com.exemplo.pedidos.pedido.dto;

import java.math.BigDecimal;

import com.exemplo.pedidos.pedido.ItemPedido;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record ItemRequest(
        @NotBlank String produto,
        @Positive int quantidade,
        @NotNull @Positive BigDecimal precoUnitario) {

    public ItemPedido paraItem() {
        return new ItemPedido(produto, quantidade, precoUnitario);
    }
}