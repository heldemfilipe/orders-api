package com.exemplo.pedidos.pedido.dto;

import java.util.List;

import com.exemplo.pedidos.pedido.StatusPedido;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

public record AtualizarPedidoRequest(
        @NotEmpty List<@Valid ItemRequest> itens,
        @NotNull StatusPedido status) {
}
