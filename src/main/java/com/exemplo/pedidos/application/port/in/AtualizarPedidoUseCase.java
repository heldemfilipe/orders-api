package com.exemplo.pedidos.application.port.in;

import java.util.List;

import com.exemplo.pedidos.domain.ItemPedido;
import com.exemplo.pedidos.domain.Pedido;
import com.exemplo.pedidos.domain.StatusPedido;

public interface AtualizarPedidoUseCase {

    Pedido atualizar(Comando comando);

    record Comando(String id, List<ItemPedido> itens, StatusPedido status) {
    }
}
