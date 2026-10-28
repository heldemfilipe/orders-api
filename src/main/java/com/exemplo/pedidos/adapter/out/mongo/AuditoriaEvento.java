package com.exemplo.pedidos.adapter.out.mongo;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.mapping.Document;

@TypeAlias("auditoria_evento")
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