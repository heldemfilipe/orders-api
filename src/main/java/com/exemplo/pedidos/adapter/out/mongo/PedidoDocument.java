package com.exemplo.pedidos.adapter.out.mongo;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.mapping.Document;

import com.exemplo.pedidos.domain.Pedido;
import com.exemplo.pedidos.domain.StatusPedido;

// o formato gravado na colecao "pedidos"; o dominio nao sabe que ele existe.
// O alias no lugar do nome da classe mantem o campo _class estavel se o pacote mudar de novo.
@TypeAlias("pedido")
@Document(collection = "pedidos")
record PedidoDocument(
        @Id String id,
        String clienteId,
        List<ItemDocument> itens,
        BigDecimal total,
        StatusPedido status,
        Instant criadoEm,
        BigDecimal frete,
        boolean freteEstimado) {

    static PedidoDocument de(Pedido pedido) {
        return new PedidoDocument(
                pedido.getId(),
                pedido.getClienteId(),
                pedido.getItens().stream().map(ItemDocument::de).toList(),
                pedido.getTotal(),
                pedido.getStatus(),
                pedido.getCriadoEm(),
                pedido.getFrete(),
                pedido.isFreteEstimado());
    }

    Pedido paraDominio() {
        Pedido pedido = new Pedido(id, clienteId, itens.stream().map(ItemDocument::paraDominio).toList(),
                total, status, criadoEm);
        pedido.definirFrete(frete, freteEstimado);
        return pedido;
    }
}
