package com.exemplo.pedidos.application.port.in;

import com.exemplo.pedidos.application.Pagina;
import com.exemplo.pedidos.application.Paginacao;
import com.exemplo.pedidos.domain.Pedido;

public interface ListarPedidosUseCase {

    Pagina<Pedido> listar(Paginacao paginacao);
}
