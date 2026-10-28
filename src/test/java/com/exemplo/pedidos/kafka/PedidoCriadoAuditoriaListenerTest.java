package com.exemplo.pedidos.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.exemplo.pedidos.auditoria.AuditoriaEvento;
import com.exemplo.pedidos.auditoria.AuditoriaEventoRepository;

@ExtendWith(MockitoExtension.class)
class PedidoCriadoAuditoriaListenerTest {

    @Mock
    AuditoriaEventoRepository repository;

    @Test
    void registraEventoParticaoOffsetEInstancia() {
        PedidoCriadoAuditoriaListener listener = new PedidoCriadoAuditoriaListener(repository, "8091");
        PedidoCriado evento = new PedidoCriado(UUID.randomUUID(), UUID.randomUUID(), "cli-1",
                List.of(new PedidoCriado.Item("caneta", 2, new BigDecimal("3.50"))),
                new BigDecimal("7.00"), Instant.now());

        listener.auditar(new ConsumerRecord<>(KafkaTopicConfig.PEDIDOS_CRIADOS, 2, 17L, "cli-1", evento));

        ArgumentCaptor<AuditoriaEvento> captor = ArgumentCaptor.forClass(AuditoriaEvento.class);
        verify(repository).save(captor.capture());
        AuditoriaEvento auditoria = captor.getValue();

        assertThat(auditoria.eventId()).isEqualTo(evento.eventId().toString());
        assertThat(auditoria.clienteId()).isEqualTo("cli-1");
        assertThat(auditoria.particao()).isEqualTo(2);
        assertThat(auditoria.offset()).isEqualTo(17L);
        assertThat(auditoria.instancia()).isEqualTo("8091");
        assertThat(auditoria.recebidoEm()).isNotNull();
    }
}