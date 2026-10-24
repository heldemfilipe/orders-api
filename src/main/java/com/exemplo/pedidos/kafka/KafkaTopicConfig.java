package com.exemplo.pedidos.kafka;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    public static final String PEDIDOS_CRIADOS = "pedidos.criados";

    @Bean
    NewTopic pedidosCriados() {
        return TopicBuilder.name(PEDIDOS_CRIADOS)
                .partitions(3)
                .replicas(1)
                .build();
    }
}