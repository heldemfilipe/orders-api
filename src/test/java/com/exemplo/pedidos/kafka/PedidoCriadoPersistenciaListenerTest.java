package com.exemplo.pedidos.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

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
import org.springframework.dao.DuplicateKeyException;

import com.exemplo.pedidos.pedido.Pedido;
import com.exemplo.pedidos.pedido.PedidoRepository;
import com.exemplo.pedidos.pedido.StatusPedido;
import com.exemplo.pedidos.frete.CotacaoFrete;
import com.exemplo.pedidos.frete.FreteClient;

@ExtendWith(MockitoExtension.class)
class PedidoCriadoPersistenciaListenerTest {

    @Mock
    PedidoRepository repository;
    @Mock FreteClient freteClient;

    @Test
    void gravaPedidoComIdDoEventoEStatusCriado() {
        when(freteClient.cotar("cli-1")).thenReturn(new CotacaoFrete(new BigDecimal("12.50"), 3));
        PedidoCriadoPersistenciaListener listener = new PedidoCriadoPersistenciaListener(repository, freteClient, "8091");
        PedidoCriado evento = evento(new BigDecimal("7.00"));

        listener.consumir(registro(evento));

        ArgumentCaptor<Pedido> captor = ArgumentCaptor.forClass(Pedido.class);
        verify(repository).insert(captor.capture());
        Pedido pedido = captor.getValue();

        assertThat(pedido.getId()).isEqualTo(evento.pedidoId().toString());
        assertThat(pedido.getClienteId()).isEqualTo("cli-1");
        assertThat(pedido.getStatus()).isEqualTo(StatusPedido.CRIADO);
        assertThat(pedido.getTotal()).isEqualByComparingTo("7.00");
        assertThat(pedido.getCriadoEm()).isEqualTo(Instant.parse("2026-10-28T15:00:00Z"));
        assertThat(pedido.getItens()).hasSize(1);
        assertThat(pedido.getFrete()).isEqualByComparingTo("12.50");
        assertThat(pedido.isFreteEstimado()).isFalse();
    }

    @Test
    void eventoReentregueEIgnoradoSemErroENuncaSubstituiOPedido() {
        when(freteClient.cotar("cli-1")).thenReturn(new CotacaoFrete(new BigDecimal("12.50"), 3));
        PedidoCriadoPersistenciaListener listener = new PedidoCriadoPersistenciaListener(repository, freteClient, "8091");
        when(repository.insert(any(Pedido.class))).thenThrow(new DuplicateKeyException("_id duplicado"));

        assertThatCode(() -> listener.consumir(registro(evento(new BigDecimal("7.00")))))
                .doesNotThrowAnyException();

        verify(repository, never()).save(any(Pedido.class));
    }

    @Test
    void totalNegativoLancaEventoInvalidoSemTocarNoBanco() {
        PedidoCriadoPersistenciaListener listener = new PedidoCriadoPersistenciaListener(repository, freteClient, "8091");

        assertThatThrownBy(() -> listener.consumir(registro(evento(new BigDecimal("-1.00")))))
                .isInstanceOf(EventoInvalidoException.class)
                .hasMessageContaining("total negativo");

        verifyNoInteractions(repository, freteClient);
    }

    private static PedidoCriado evento(BigDecimal total) {
        return new PedidoCriado(UUID.randomUUID(), UUID.randomUUID(), "cli-1",
                List.of(new PedidoCriado.Item("caneta", 2, new BigDecimal("3.50"))),
                total, Instant.parse("2026-10-28T15:00:00Z"));
    }

    private static ConsumerRecord<String, PedidoCriado> registro(PedidoCriado evento) {
        return new ConsumerRecord<>(KafkaTopicConfig.PEDIDOS_CRIADOS, 2, 17L, "cli-1", evento);
    }
}