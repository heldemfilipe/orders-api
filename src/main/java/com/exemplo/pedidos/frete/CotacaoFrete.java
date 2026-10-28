package com.exemplo.pedidos.frete;

import java.math.BigDecimal;

public record CotacaoFrete(BigDecimal valor, int prazoDias) {
}