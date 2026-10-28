package com.exemplo.pedidos.adapter.out.http;

import java.math.BigDecimal;

public record CotacaoFrete(BigDecimal valor, int prazoDias) {
}