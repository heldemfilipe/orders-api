package com.exemplo.pedidos.application.port.out;

import com.exemplo.pedidos.domain.PedidoCriadoEvento;

public interface EventoPublisherPort {

    // espera a confirmacao do broker; lanca EventoNaoPublicadoException se nao vier
    void publicar(PedidoCriadoEvento evento);
}
