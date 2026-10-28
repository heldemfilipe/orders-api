package com.exemplo.pedidos.kafka;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;

@Configuration
public class KafkaErroConfig {

    public static final String PEDIDOS_CRIADOS_DLT = KafkaTopicConfig.PEDIDOS_CRIADOS + ".DLT";

    @Bean
    NewTopic pedidosCriadosDlt() {
        return TopicBuilder.name(PEDIDOS_CRIADOS_DLT)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    DefaultErrorHandler errorHandler(KafkaTemplate<Object, Object> template) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(template,
                (registro, erro) -> registro.topic().equals(PEDIDOS_CRIADOS_DLT)
                        ? null
                        : new TopicPartition(PEDIDOS_CRIADOS_DLT, registro.partition()));
                        
        ExponentialBackOffWithMaxRetries espera = new ExponentialBackOffWithMaxRetries(3);
        espera.setInitialInterval(1_000);
        espera.setMultiplier(2.0);
        espera.setMaxInterval(10_000);

        return new DefaultErrorHandler(recoverer, espera);
    }
}