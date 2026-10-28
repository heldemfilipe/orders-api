package com.exemplo.pedidos.falha;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface MensagemFalhaRepository extends MongoRepository<MensagemFalha, String> {
}