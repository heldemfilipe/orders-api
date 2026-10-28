package com.exemplo.pedidos.adapter.in.kafka;

import java.time.Instant;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.exemplo.pedidos.adapter.kafka.KafkaTopicConfig;
import com.exemplo.pedidos.adapter.kafka.PedidoCriado;
import com.exemplo.pedidos.application.RegistroAuditoria;
import com.exemplo.pedidos.application.port.in.RegistrarAuditoriaUseCase;

@Profile("!cloud")
@Component
public class PedidoCriadoAuditoriaListener {

    private static final Logger log = LoggerFactory.getLogger(PedidoCriadoAuditoriaListener.class);

    private final RegistrarAuditoriaUseCase useCase;
    private final String instancia;

    public PedidoCriadoAuditoriaListener(RegistrarAuditoriaUseCase useCase,
                                         @Value("${server.port:8080}") String instancia) {
        this.useCase = useCase;
        this.instancia = instancia;
    }

    @KafkaListener(topics = KafkaTopicConfig.PEDIDOS_CRIADOS, groupId = "auditoria")
    public void auditar(ConsumerRecord<String, PedidoCriado> registro) {
        PedidoCriado evento = registro.value();
        useCase.registrar(new RegistroAuditoria(evento.eventId().toString(), evento.clienteId(),
                registro.partition(), registro.offset(), Instant.now(), instancia));

        log.info("[auditoria][instancia {}] evento {} registrado (cliente {}, particao {}, offset {})",
                instancia, evento.eventId(), evento.clienteId(), registro.partition(), registro.offset());
    }
}
