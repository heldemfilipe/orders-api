package com.exemplo.pedidos.pedido;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import com.exemplo.pedidos.erro.EventoNaoPublicadoException;
import com.exemplo.pedidos.pedido.dto.PedidoAceito;
import com.exemplo.pedidos.pedido.dto.PedidoRequest;

@WebMvcTest(PedidoEventoController.class)
class PedidoEventoControllerTest {

    private static final String CORPO_VALIDO = """
            {"clienteId":"cli-1","itens":[{"produto":"caneta","quantidade":2,"precoUnitario":3.50}]}
            """;

    @Autowired
    MockMvcTester mvc;

    @MockitoBean
    PedidoEventoService service;

    @Test
    void pedidoValidoDevolve202ComOsIds() {
        UUID pedidoId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        when(service.aceitar(any(PedidoRequest.class))).thenReturn(new PedidoAceito(pedidoId, eventId));

        assertThat(mvc.post().uri("/pedidos/eventos")
                .contentType(MediaType.APPLICATION_JSON)
                .content(CORPO_VALIDO))
                .hasStatus(HttpStatus.ACCEPTED)
                .bodyJson().extractingPath("$.pedidoId").isEqualTo(pedidoId.toString());
    }

    @Test
    void pedidoInvalidoDevolve400ENaoPublica() {
        assertThat(mvc.post().uri("/pedidos/eventos")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"clienteId\":\"\",\"itens\":[]}"))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON);

        verify(service, never()).aceitar(any());
    }

    @Test
    void filaForaDoArDevolve503() {
        when(service.aceitar(any(PedidoRequest.class)))
                .thenThrow(new EventoNaoPublicadoException(UUID.randomUUID(), new RuntimeException("kafka fora")));

        assertThat(mvc.post().uri("/pedidos/eventos")
                .contentType(MediaType.APPLICATION_JSON)
                .content(CORPO_VALIDO))
                .hasStatus(HttpStatus.SERVICE_UNAVAILABLE)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
    }
}