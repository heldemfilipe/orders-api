package com.exemplo.pedidos.pedido;

public enum StatusPedido {
    CRIADO,
    PAGO,
    CANCELADO;

    public boolean podeTransicionarPara(StatusPedido destino) {
        return switch (this) {
            case CRIADO -> true;
            case PAGO -> destino == CANCELADO;
            case CANCELADO -> false;
        };
    }
}