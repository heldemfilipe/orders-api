package com.exemplo.pedidos.application.erro;

public class PedidoNaoEncontradoException extends RuntimeException {

    public PedidoNaoEncontradoException(String id) {
        super("Pedido não encontrado: " + id);
    }
}
