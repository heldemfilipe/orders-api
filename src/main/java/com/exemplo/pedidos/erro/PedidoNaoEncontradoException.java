package com.exemplo.pedidos.erro;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

// Provisório: na Parte 6 o @RestControllerAdvice assume e o @ResponseStatus sai.
@ResponseStatus(HttpStatus.NOT_FOUND)
public class PedidoNaoEncontradoException extends RuntimeException {

    public PedidoNaoEncontradoException(String id) {
        super("Pedido não encontrado: " + id);
    }
}
