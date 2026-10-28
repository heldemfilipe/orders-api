package com.exemplo.pedidos.kafka;

import java.time.Instant;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.exemplo.pedidos.auditoria.AuditoriaEvento;
import com.exemplo.pedidos.auditoria.AuditoriaEventoRepository;

@Profile("!cloud")
@Component
public class PedidoCriadoAuditoriaListener {

    private static final Logger log = LoggerFactory.getLogger(PedidoCriadoAuditoriaListener.class);

    private final AuditoriaEventoRepository repository;
    private final String instancia;

    public PedidoCriadoAuditoriaListener(AuditoriaEventoRepository repository,
                                         @Value("${server.port:8080}") String instancia) {
        this.repository = repository;
        this.instancia = instancia;
    }

    @KafkaListener(topics = KafkaTopicConfig.PEDIDOS_CRIADOS, groupId = "auditoria")
    public void auditar(ConsumerRecord<String, PedidoCriado> registro) {
        PedidoCriado evento = registro.value();
        repository.save(new AuditoriaEvento(null, evento.eventId().toString(), evento.clienteId(),
                registro.partition(), registro.offset(), Instant.now(), instancia));

        log.info("[auditoria][instancia {}] evento {} registrado (cliente {}, particao {}, offset {})",
                instancia, evento.eventId(), evento.clienteId(), registro.partition(), registro.offset());
    }
}
