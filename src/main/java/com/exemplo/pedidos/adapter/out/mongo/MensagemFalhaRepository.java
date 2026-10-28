package com.exemplo.pedidos.adapter.out.mongo;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface MensagemFalhaRepository extends MongoRepository<MensagemFalha, String> {
}