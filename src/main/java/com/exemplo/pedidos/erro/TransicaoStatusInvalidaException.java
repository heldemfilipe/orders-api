package com.exemplo.pedidos.erro;

import com.exemplo.pedidos.pedido.StatusPedido;

public class TransicaoStatusInvalidaException extends RuntimeException {

    public TransicaoStatusInvalidaException(StatusPedido atual, StatusPedido destino) {
        super("Transição de status inválida: " + atual + " -> " + destino);
    }
}