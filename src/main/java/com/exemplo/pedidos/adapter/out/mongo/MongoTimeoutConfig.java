package com.exemplo.pedidos.adapter.out.mongo;

import java.util.concurrent.TimeUnit;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.mongodb.autoconfigure.MongoClientSettingsBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(MongoProperties.class)
class MongoTimeoutConfig {

    // a URI do Mongo vem de um segredo no Kubernetes: o tempo nao pode depender de parametro nela
    @Bean
    MongoClientSettingsBuilderCustomizer selecaoDeServidorRapida(MongoProperties propriedades) {
        return builder -> builder.applyToClusterSettings(cluster -> cluster
                .serverSelectionTimeout(propriedades.serverSelectionTimeout().toMillis(), TimeUnit.MILLISECONDS));
    }
}
