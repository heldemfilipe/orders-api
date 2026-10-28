package com.exemplo.pedidos.falha;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "mensagens_falhas")
public record MensagemFalha(
        @Id String id,
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