package com.exemplo.pedidos.application.port.in;

import java.util.List;
import java.util.UUID;

import com.exemplo.pedidos.domain.ItemPedido;

public interface AceitarPedidoUseCase {

    Aceito aceitar(Comando comando);

    record Comando(String clienteId, List<ItemPedido> itens) {
    }

    record Aceito(UUID pedidoId, UUID eventId) {
    }
}
