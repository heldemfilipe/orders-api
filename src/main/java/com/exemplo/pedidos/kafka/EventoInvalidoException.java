package com.exemplo.pedidos.kafka;

public class EventoInvalidoException extends RuntimeException {

    public EventoInvalidoException(String motivo) {
        super(motivo);
    }
}