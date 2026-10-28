package com.exemplo.pedidos.frete;

import java.math.BigDecimal;

public record Frete(BigDecimal valor, int prazoDias, boolean estimado) {

    public static final Frete PADRAO = new Frete(new BigDecimal("20.00"), 7, true);

    static Frete de(CotacaoFrete cotacao) {
        return new Frete(cotacao.valor(), cotacao.prazoDias(), false);
    }
}