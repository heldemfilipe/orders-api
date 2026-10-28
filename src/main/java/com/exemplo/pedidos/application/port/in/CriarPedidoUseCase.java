package com.exemplo.pedidos.application.port.in;

import java.util.List;

import com.exemplo.pedidos.domain.ItemPedido;
import com.exemplo.pedidos.domain.Pedido;

public interface CriarPedidoUseCase {

    Pedido criar(Comando comando);

    record Comando(String clienteId, List<ItemPedido> itens) {
    }
}
