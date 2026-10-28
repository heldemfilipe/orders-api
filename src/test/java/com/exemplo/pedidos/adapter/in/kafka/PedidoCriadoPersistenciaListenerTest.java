package com.exemplo.pedidos.adapter.in.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
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

import com.exemplo.pedidos.adapter.kafka.KafkaTopicConfig;
import com.exemplo.pedidos.adapter.kafka.PedidoCriado;
import com.exemplo.pedidos.application.erro.EventoInvalidoException;
import com.exemplo.pedidos.application.port.in.RegistrarPedidoCriadoUseCase;
import com.exemplo.pedidos.application.port.in.RegistrarPedidoCriadoUseCase.Resultado;
import com.exemplo.pedidos.domain.PedidoCriadoEvento;

// a regra de gravar o pedido tem teste proprio em RegistrarPedidoCriadoServiceTest;
// aqui so se confere a traducao do registro do Kafka para o evento de dominio
@ExtendWith(MockitoExtension.class)
class PedidoCriadoPersistenciaListenerTest {

    @Mock
    RegistrarPedidoCriadoUseCase useCase;

    @Test
    void traduzOEventoDoKafkaParaOEventoDeDominio() {
        when(useCase.registrar(any(PedidoCriadoEvento.class))).thenReturn(Resultado.GRAVADO);
        PedidoCriadoPersistenciaListener listener = new PedidoCriadoPersistenciaListener(useCase, "8091");
        PedidoCriado evento = evento(new BigDecimal("7.00"));

        listener.consumir(registro(evento));

        ArgumentCaptor<PedidoCriadoEvento> captor = ArgumentCaptor.forClass(PedidoCriadoEvento.class);
        verify(useCase).registrar(captor.capture());
        PedidoCriadoEvento recebido = captor.getValue();
        assertThat(recebido.eventId()).isEqualTo(evento.eventId());
        assertThat(recebido.pedidoId()).isEqualTo(evento.pedidoId());
        assertThat(recebido.clienteId()).isEqualTo("cli-1");
        assertThat(recebido.total()).isEqualByComparingTo("7.00");
        assertThat(recebido.ocorridoEm()).isEqualTo(Instant.parse("2026-10-28T15:00:00Z"));
        assertThat(recebido.itens()).singleElement().satisfies(item -> {
            assertThat(item.produto()).isEqualTo("caneta");
            assertThat(item.quantidade()).isEqualTo(2);
        });
    }

    @Test
    void eventoReentregueNaoEErro() {
        when(useCase.registrar(any(PedidoCriadoEvento.class))).thenReturn(Resultado.JA_EXISTIA);
        PedidoCriadoPersistenciaListener listener = new PedidoCriadoPersistenciaListener(useCase, "8091");

        assertThatCode(() -> listener.consumir(registro(evento(new BigDecimal("7.00")))))
                .doesNotThrowAnyException();
    }

    @Test
    void eventoInvalidoPropagaParaOTratamentoDeErrosDoKafka() {
        when(useCase.registrar(any(PedidoCriadoEvento.class)))
                .thenThrow(new EventoInvalidoException("total negativo: -1.00"));
        PedidoCriadoPersistenciaListener listener = new PedidoCriadoPersistenciaListener(useCase, "8091");

        assertThatThrownBy(() -> listener.consumir(registro(evento(new BigDecimal("-1.00")))))
                .isInstanceOf(EventoInvalidoException.class)
                .hasMessageContaining("total negativo");
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
