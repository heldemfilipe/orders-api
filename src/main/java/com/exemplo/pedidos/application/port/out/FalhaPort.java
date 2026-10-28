package com.exemplo.pedidos.application.port.out;

import com.exemplo.pedidos.application.RegistroFalha;

public interface FalhaPort {

    void salvar(RegistroFalha falha);
}
