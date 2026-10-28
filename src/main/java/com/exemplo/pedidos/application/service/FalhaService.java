package com.exemplo.pedidos.application.service;

import com.exemplo.pedidos.application.RegistroFalha;
import com.exemplo.pedidos.application.port.in.RegistrarFalhaUseCase;
import com.exemplo.pedidos.application.port.out.FalhaPort;

public class FalhaService implements RegistrarFalhaUseCase {

    private final FalhaPort falhaPort;

    public FalhaService(FalhaPort falhaPort) {
        this.falhaPort = falhaPort;
    }

    @Override
    public void registrar(RegistroFalha falha) {
        falhaPort.salvar(falha);
    }
}
