package com.exemplo.pedidos.pedido;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "pedidos")
public class Pedido {

    @Id
    private String id;
    private String clienteId;
    private List<ItemPedido> itens;
    private BigDecimal total;
    private StatusPedido status;
    private Instant criadoEm;

    public Pedido(String id, String clienteId, List<ItemPedido> itens, BigDecimal total,
                  StatusPedido status, Instant criadoEm) {
        this.id = id;
        this.clienteId = clienteId;
        this.itens = List.copyOf(itens);
        this.total = total;
        this.status = status;
        this.criadoEm = criadoEm;
    }

    public static Pedido novo(String clienteId, List<ItemPedido> itens, BigDecimal total) {
        return new Pedido(null, clienteId, itens, total, StatusPedido.CRIADO, Instant.now());
    }

    public static BigDecimal calcularTotal(List<ItemPedido> itens) {
        return itens.stream()
                .map(i -> i.precoUnitario().multiply(BigDecimal.valueOf(i.quantidade())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public void atualizar(List<ItemPedido> itens, BigDecimal total, StatusPedido status) {
        this.itens = List.copyOf(itens);
        this.total = total;
        this.status = status;
    }

    public String getId() {
        return id;
    }

    public String getClienteId() {
        return clienteId;
    }

    public List<ItemPedido> getItens() {
        return itens;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public StatusPedido getStatus() {
        return status;
    }

    public Instant getCriadoEm() {
        return criadoEm;
    }
}
