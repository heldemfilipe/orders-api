package com.exemplo.pedidos.auditoria;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "auditoria_eventos")
public record AuditoriaEvento(
        @Id String id,
        String eventId,
        String clienteId,
        int particao,
        long offset,
        Instant recebidoEm,
        String instancia) {
}