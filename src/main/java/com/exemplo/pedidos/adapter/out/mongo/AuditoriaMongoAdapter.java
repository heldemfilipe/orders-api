package com.exemplo.pedidos.adapter.out.mongo;

import org.springframework.stereotype.Component;

import com.exemplo.pedidos.application.RegistroAuditoria;
import com.exemplo.pedidos.application.port.out.AuditoriaPort;

@Component
class AuditoriaMongoAdapter implements AuditoriaPort {

    private final AuditoriaEventoRepository repository;

    AuditoriaMongoAdapter(AuditoriaEventoRepository repository) {
        this.repository = repository;
    }

    @Override
    public void salvar(RegistroAuditoria registro) {
        repository.save(new AuditoriaEvento(null, registro.eventId(), registro.clienteId(),
                registro.particao(), registro.offset(), registro.recebidoEm(), registro.instancia()));
    }
}
