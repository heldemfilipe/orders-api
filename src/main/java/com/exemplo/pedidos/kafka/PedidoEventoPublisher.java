package com.exemplo.pedidos.kafka;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class PedidoEventoPublisher {

    private static final Logger log = LoggerFactory.getLogger(PedidoEventoPublisher.class);

    private final KafkaTemplate<String, PedidoCriado> kafkaTemplate;

    public PedidoEventoPublisher(KafkaTemplate<String, PedidoCriado> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publicar(PedidoCriado evento) {
        kafkaTemplate.send(KafkaTopicConfig.PEDIDOS_CRIADOS, evento.clienteId(), evento)
                .whenComplete((resultado, erro) -> {
                    if (erro != null) {
                        log.error("Falha ao publicar evento {}", evento.eventId(), erro);
                    } else {
                        var meta = resultado.getRecordMetadata();
                        log.info("Evento {} publicado: particao {}, offset {}",
                                evento.eventId(), meta.partition(), meta.offset());
                    }
                });
    }
}