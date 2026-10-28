package com.exemplo.pedidos.application;

import java.util.List;
import java.util.function.Function;

public record Pagina<T>(List<T> conteudo, int numero, int tamanho, long totalElementos) {

    public <R> Pagina<R> mapear(Function<T, R> conversor) {
        return new Pagina<>(conteudo.stream().map(conversor).toList(), numero, tamanho, totalElementos);
    }
}
