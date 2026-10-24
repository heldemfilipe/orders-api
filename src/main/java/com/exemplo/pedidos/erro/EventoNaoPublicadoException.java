package com.exemplo.pedidos.erro;

import java.util.UUID;

public class EventoNaoPublicadoException extends RuntimeException {

    public EventoNaoPublicadoException(UUID eventId, Throwable causa) {
        super("Não foi possível publicar o evento " + eventId, causa);
    }
}