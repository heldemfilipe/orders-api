package com.exemplo.pedidos.auditoria;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface AuditoriaEventoRepository extends MongoRepository<AuditoriaEvento, String> {
}