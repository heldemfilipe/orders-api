package com.exemplo.pedidos.application.port.out;

import com.exemplo.pedidos.application.RegistroAuditoria;

public interface AuditoriaPort {

    void salvar(RegistroAuditoria registro);
}
