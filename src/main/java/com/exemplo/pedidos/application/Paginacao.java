package com.exemplo.pedidos.application;

import java.util.List;

public record Paginacao(int numero, int tamanho, List<Ordem> ordens) {

    public record Ordem(String campo, boolean ascendente) {
    }
}
