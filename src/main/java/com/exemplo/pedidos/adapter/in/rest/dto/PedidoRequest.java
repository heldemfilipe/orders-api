package com.exemplo.pedidos.adapter.in.rest.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

public record PedidoRequest(
        @NotBlank String clienteId,
        @NotEmpty List<@Valid ItemRequest> itens) {
}
