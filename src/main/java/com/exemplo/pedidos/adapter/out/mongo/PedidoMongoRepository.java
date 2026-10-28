package com.exemplo.pedidos.adapter.out.mongo;

import org.springframework.data.mongodb.repository.MongoRepository;

interface PedidoMongoRepository extends MongoRepository<PedidoDocument, String> {
}
