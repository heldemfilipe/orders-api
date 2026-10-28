package com.exemplo.pedidos.application.port.out;

import java.util.Optional;

import com.exemplo.pedidos.application.Pagina;
import com.exemplo.pedidos.application.Paginacao;
import com.exemplo.pedidos.domain.Pedido;

public interface PedidoRepositoryPort {

    // grava ou substitui; num pedido novo, atribui o id gerado
    Pedido salvar(Pedido pedido);

    // grava so se ainda nao existe; lanca PedidoJaExisteException se o id ja estiver ocupado
    void inserir(Pedido pedido);

    Optional<Pedido> buscarPorId(String id);

    Pagina<Pedido> listar(Paginacao paginacao);

    boolean existe(String id);

    void remover(String id);
}
