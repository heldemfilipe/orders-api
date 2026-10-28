package com.exemplo.pedidos.application;

import java.time.Instant;

public record RegistroFalha(
        String topicoOriginal,
        int particaoOriginal,
        long offsetOriginal,
        String grupoOriginal,
        String chave,
        String payload,
        String excecao,
        String motivo,
        Instant recebidoEm) {
}
