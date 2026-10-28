package com.exemplo.pedidos.application.erro;

public class EventoInvalidoException extends RuntimeException {

    public EventoInvalidoException(String motivo) {
        super(motivo);
    }
}
