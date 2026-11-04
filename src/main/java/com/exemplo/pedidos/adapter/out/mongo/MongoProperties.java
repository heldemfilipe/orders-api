package com.exemplo.pedidos.adapter.out.mongo;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Tempo que o driver espera por um servidor do MongoDB antes de desistir. O padrao do driver e 30 s:
 * com o banco fora do ar, cada requisicao ficaria presa esse tempo e esgotaria as threads.
 */
@ConfigurationProperties("pedidos.mongodb")
public record MongoProperties(Duration serverSelectionTimeout) {

    static final Duration PADRAO = Duration.ofSeconds(3);

    public MongoProperties {
        if (serverSelectionTimeout == null) {
            serverSelectionTimeout = PADRAO;
        }
    }
}
