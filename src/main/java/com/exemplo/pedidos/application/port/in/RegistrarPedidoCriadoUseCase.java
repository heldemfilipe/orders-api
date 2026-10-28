package com.exemplo.pedidos.application.port.in;

import com.exemplo.pedidos.domain.PedidoCriadoEvento;

public interface RegistrarPedidoCriadoUseCase {

    // lanca EventoInvalidoException se o evento viola uma regra; reentrega nao e erro, e JA_EXISTIA
    Resultado registrar(PedidoCriadoEvento evento);

    enum Resultado {
        GRAVADO,
        JA_EXISTIA
    }
}
