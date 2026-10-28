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

import com.exemplo.pedidos.pedido.Pedido;
import com.exemplo.pedidos.pedido.PedidoRepository;
import com.exemplo.pedidos.pedido.StatusPedido;

@ExtendWith(MockitoExtension.class)
class PedidoCriadoPersistenciaListenerTest {

    @Mock
    PedidoRepository repository;

    @Test
    void gravaPedidoComIdDoEventoEStatusCriado() {
        PedidoCriadoPersistenciaListener listener = new PedidoCriadoPersistenciaListener(repository, "8091");
        PedidoCriado evento = new PedidoCriado(UUID.randomUUID(), UUID.randomUUID(), "cli-1",
                List.of(new PedidoCriado.Item("caneta", 2, new BigDecimal("3.50"))),
                new BigDecimal("7.00"), Instant.parse("2026-10-28T15:00:00Z"));

        listener.consumir(new ConsumerRecord<>(KafkaTopicConfig.PEDIDOS_CRIADOS, 2, 17L, "cli-1", evento));

        ArgumentCaptor<Pedido> captor = ArgumentCaptor.forClass(Pedido.class);
        verify(repository).save(captor.capture());
        Pedido pedido = captor.getValue();

        assertThat(pedido.getId()).isEqualTo(evento.pedidoId().toString());
        assertThat(pedido.getClienteId()).isEqualTo("cli-1");
        assertThat(pedido.getStatus()).isEqualTo(StatusPedido.CRIADO);
        assertThat(pedido.getTotal()).isEqualByComparingTo("7.00");
        assertThat(pedido.getCriadoEm()).isEqualTo(Instant.parse("2026-10-28T15:00:00Z"));
        assertThat(pedido.getItens()).hasSize(1);
    }
}