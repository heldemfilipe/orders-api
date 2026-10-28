package com.exemplo.pedidos.application.service;

import com.exemplo.pedidos.application.RegistroAuditoria;
import com.exemplo.pedidos.application.port.in.RegistrarAuditoriaUseCase;
import com.exemplo.pedidos.application.port.out.AuditoriaPort;

public class AuditoriaService implements RegistrarAuditoriaUseCase {

    private final AuditoriaPort auditoriaPort;

    public AuditoriaService(AuditoriaPort auditoriaPort) {
        this.auditoriaPort = auditoriaPort;
    }

    @Override
    public void registrar(RegistroAuditoria registro) {
        auditoriaPort.salvar(registro);
    }
}
