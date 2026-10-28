package com.exemplo.pedidos.kafka;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import com.exemplo.pedidos.erro.EventoNaoPublicadoException;

@Profile("!cloud")
@Component
public class PedidoEventoPublisher {

    private static final Logger log = LoggerFactory.getLogger(PedidoEventoPublisher.class);

    private final KafkaTemplate<String, PedidoCriado> kafkaTemplate;

    public PedidoEventoPublisher(KafkaTemplate<String, PedidoCriado> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

        public void publicar(PedidoCriado evento) {
        try {
            CompletableFuture<SendResult<String, PedidoCriado>> envio =
                    kafkaTemplate.send(KafkaTopicConfig.PEDIDOS_CRIADOS, evento.clienteId(), evento);

            envio.whenComplete((resultado, erro) -> {
                if (erro != null) {
                    log.error("Falha ao publicar evento {}", evento.eventId(), erro);
                } else {
                    var meta = resultado.getRecordMetadata();
                    log.info("Evento {} publicado: particao {}, offset {}",
                            evento.eventId(), meta.partition(), meta.offset());
                }
            });

            envio.get(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new EventoNaoPublicadoException(evento.eventId(), e);
        } catch (ExecutionException | TimeoutException | KafkaException e) {
            log.error("Evento {} nao publicado", evento.eventId(), e);
            throw new EventoNaoPublicadoException(evento.eventId(), e);
        }
    }
}
