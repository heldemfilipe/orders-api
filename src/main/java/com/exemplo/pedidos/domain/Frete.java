package com.exemplo.pedidos.domain;

import java.math.BigDecimal;

public record Frete(BigDecimal valor, int prazoDias, boolean estimado) {

    public static final Frete PADRAO = new Frete(new BigDecimal("20.00"), 7, true);
}
