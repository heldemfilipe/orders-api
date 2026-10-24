package com.exemplo.pedidos.pedido;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import static org.mockito.Mockito.verify;
import org.mockito.junit.jupiter.MockitoExtension;

import com.exemplo.pedidos.kafka.PedidoCriado;
import com.exemplo.pedidos.kafka.PedidoEventoPublisher;
import com.exemplo.pedidos.pedido.dto.ItemRequest;
import com.exemplo.pedidos.pedido.dto.PedidoAceito;
import com.exemplo.pedidos.pedido.dto.PedidoRequest;

@ExtendWith(MockitoExtension.class)
class PedidoEventoServiceTest {

    @Mock
    PedidoEventoPublisher publisher;

    @Test
    void aceitarPublicaEventoComTotalCalculadoEDevolveOsIds() {
        PedidoEventoService service = new PedidoEventoService(publisher);
        PedidoRequest request = new PedidoRequest("cli-1", List.of(
                new ItemRequest("caneta", 2, new BigDecimal("3.50")),
                new ItemRequest("caderno", 1, new BigDecimal("12.90"))));

        PedidoAceito aceito = service.aceitar(request);

        ArgumentCaptor<PedidoCriado> captor = ArgumentCaptor.forClass(PedidoCriado.class);
        verify(publisher).publicar(captor.capture());
        PedidoCriado evento = captor.getValue();

        assertThat(evento.clienteId()).isEqualTo("cli-1");
        assertThat(evento.total()).isEqualByComparingTo("19.90");
        assertThat(evento.itens()).hasSize(2);
        assertThat(evento.eventId()).isNotEqualTo(evento.pedidoId());
        assertThat(aceito.eventId()).isEqualTo(evento.eventId());
        assertThat(aceito.pedidoId()).isEqualTo(evento.pedidoId());
    }
}