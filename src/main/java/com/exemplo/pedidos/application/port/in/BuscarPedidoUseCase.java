package com.exemplo.pedidos.application.port.in;

import com.exemplo.pedidos.domain.Pedido;

public interface BuscarPedidoUseCase {

    Pedido buscar(String id);
}
