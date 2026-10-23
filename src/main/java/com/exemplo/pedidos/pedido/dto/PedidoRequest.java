package com.exemplo.pedidos.pedido.dto;

import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

public record PedidoRequest(
        @NotBlank String clienteId,
        @NotEmpty @Valid List<ItemRequest> itens) {
}
