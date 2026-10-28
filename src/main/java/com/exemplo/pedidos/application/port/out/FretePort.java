package com.exemplo.pedidos.application.port.out;

import com.exemplo.pedidos.domain.Frete;

public interface FretePort {

    // nunca lanca por indisponibilidade do servico: quem implementa decide o que fazer (ex.: Frete.PADRAO)
    Frete cotar(String clienteId);
}
