package com.exemplo.pedidos.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public class Pedido {

    private String id;
    private final String clienteId;
    private List<ItemPedido> itens;
    private BigDecimal total;
    private StatusPedido status;
    private final Instant criadoEm;
    private BigDecimal frete;
    private boolean freteEstimado;

    public Pedido(String id, String clienteId, List<ItemPedido> itens, BigDecimal total,
                  StatusPedido status, Instant criadoEm) {
        this.id = id;
        this.clienteId = clienteId;
        this.itens = List.copyOf(itens);
        this.total = total;
        this.status = status;
        this.criadoEm = criadoEm;
    }

    public static Pedido novo(String clienteId, List<ItemPedido> itens) {
        return new Pedido(null, clienteId, itens, calcularTotal(itens), StatusPedido.CRIADO, Instant.now());
    }

    public static BigDecimal calcularTotal(List<ItemPedido> itens) {
        return itens.stream()
                .map(i -> i.precoUnitario().multiply(BigDecimal.valueOf(i.quantidade())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public void atualizar(List<ItemPedido> itens, StatusPedido novoStatus) {
        if (!status.podeTransicionarPara(novoStatus)) {
            throw new TransicaoStatusInvalidaException(status, novoStatus);
        }
        this.itens = List.copyOf(itens);
        this.total = calcularTotal(itens);
        this.status = novoStatus;
    }

    public void definirFrete(BigDecimal frete, boolean estimado) {
        this.frete = frete;
        this.freteEstimado = estimado;
    }

    // o id nasce na persistencia: so e atribuido uma vez, ao gravar um pedido novo
    public void atribuirId(String id) {
        if (this.id != null) {
            throw new IllegalStateException("pedido " + this.id + " ja tem id");
        }
        this.id = id;
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

    public BigDecimal getFrete() {
        return frete;
    }

    public boolean isFreteEstimado() {
        return freteEstimado;
    }
}
