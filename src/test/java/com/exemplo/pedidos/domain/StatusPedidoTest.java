package com.exemplo.pedidos.domain;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class StatusPedidoTest {

    @ParameterizedTest(name = "{0} -> {1} permitido = {2}")
    @CsvSource({
            "CRIADO,    CRIADO,    true",
            "CRIADO,    PAGO,      true",
            "CRIADO,    CANCELADO, true",
            "PAGO,      CRIADO,    false",
            "PAGO,      PAGO,      false",
            "PAGO,      CANCELADO, true",
            "CANCELADO, CRIADO,    false",
            "CANCELADO, PAGO,      false",
            "CANCELADO, CANCELADO, false"
    })
    void transicoes(StatusPedido atual, StatusPedido destino, boolean permitido) {
        assertThat(atual.podeTransicionarPara(destino)).isEqualTo(permitido);
    }
}