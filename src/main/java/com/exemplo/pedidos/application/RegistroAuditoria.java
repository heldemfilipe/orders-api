package com.exemplo.pedidos.application;

import java.time.Instant;

public record RegistroAuditoria(
        String eventId,
        String clienteId,
        int particao,
        long offset,
        Instant recebidoEm,
        String instancia) {
}
