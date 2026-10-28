package com.exemplo.pedidos.adapter.out.kafka;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.core.KafkaTemplate;

import com.exemplo.pedidos.adapter.kafka.PedidoCriado;
import com.exemplo.pedidos.application.erro.EventoNaoPublicadoException;
import com.exemplo.pedidos.domain.ItemPedido;
import com.exemplo.pedidos.domain.PedidoCriadoEvento;

@ExtendWith(MockitoExtension.class)
class PedidoEventoPublisherTest {

    @Mock
    KafkaTemplate<String, PedidoCriado> kafkaTemplate;

    PedidoEventoPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new PedidoEventoPublisher(kafkaTemplate);
    }

    @Test
    void falhaNoFuturoViraEventoNaoPublicado() {
        when(kafkaTemplate.send(anyString(), anyString(), any(PedidoCriado.class)))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("broker fora")));

        assertThatThrownBy(() -> publisher.publicar(evento()))
                .isInstanceOf(EventoNaoPublicadoException.class);
    }

    @Test
    void excecaoSincronaDoSendViraEventoNaoPublicado() {
        when(kafkaTemplate.send(anyString(), anyString(), any(PedidoCriado.class)))
                .thenThrow(new KafkaException("Send failed"));

        assertThatThrownBy(() -> publisher.publicar(evento()))
                .isInstanceOf(EventoNaoPublicadoException.class);
    }

    private PedidoCriadoEvento evento() {
        return new PedidoCriadoEvento(UUID.randomUUID(), UUID.randomUUID(), "cli-1",
                List.of(new ItemPedido("caneta", 2, new BigDecimal("3.50"))),
                new BigDecimal("7.00"), Instant.now());
    }
}