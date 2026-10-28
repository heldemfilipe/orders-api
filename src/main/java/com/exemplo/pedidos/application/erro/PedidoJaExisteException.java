package com.exemplo.pedidos.application.erro;

public class PedidoJaExisteException extends RuntimeException {

    public PedidoJaExisteException(String id) {
        super("Pedido já existe: " + id);
    }
}
