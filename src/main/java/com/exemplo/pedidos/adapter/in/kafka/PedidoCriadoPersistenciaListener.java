package com.exemplo.pedidos.adapter.in.kafka;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.exemplo.pedidos.adapter.kafka.KafkaTopicConfig;
import com.exemplo.pedidos.adapter.kafka.PedidoCriado;
import com.exemplo.pedidos.application.port.in.RegistrarPedidoCriadoUseCase;

@Profile("!cloud")
@Component
public class PedidoCriadoPersistenciaListener {

    private static final Logger log = LoggerFactory.getLogger(PedidoCriadoPersistenciaListener.class);

    private final RegistrarPedidoCriadoUseCase useCase;
    private final String instancia;

    public PedidoCriadoPersistenciaListener(RegistrarPedidoCriadoUseCase useCase,
                                            @Value("${server.port:8080}") String instancia) {
        this.useCase = useCase;
        this.instancia = instancia;
    }

    @KafkaListener(topics = KafkaTopicConfig.PEDIDOS_CRIADOS, groupId = "persistencia")
    public void consumir(ConsumerRecord<String, PedidoCriado> registro) {
        PedidoCriado evento = registro.value();

        switch (useCase.registrar(evento.paraEvento())) {
            case GRAVADO -> log.info("[persistencia][instancia {}] pedido {} gravado (cliente {}, particao {}, offset {})",
                    instancia, evento.pedidoId(), evento.clienteId(), registro.partition(), registro.offset());
            case JA_EXISTIA -> log.warn("[persistencia][instancia {}] pedido {} ja existe (evento {} reentregue), ignorando",
                    instancia, evento.pedidoId(), evento.eventId());
        }
    }
}
