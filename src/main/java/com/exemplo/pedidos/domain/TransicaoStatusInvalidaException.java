package com.exemplo.pedidos.domain;

public class TransicaoStatusInvalidaException extends RuntimeException {

    public TransicaoStatusInvalidaException(StatusPedido atual, StatusPedido destino) {
        super("Transição de status inválida: " + atual + " -> " + destino);
    }
}
