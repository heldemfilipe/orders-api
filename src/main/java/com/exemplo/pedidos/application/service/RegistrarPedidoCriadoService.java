package com.exemplo.pedidos.application.service;

import com.exemplo.pedidos.application.erro.EventoInvalidoException;
import com.exemplo.pedidos.application.erro.PedidoJaExisteException;
import com.exemplo.pedidos.application.port.in.RegistrarPedidoCriadoUseCase;
import com.exemplo.pedidos.application.port.out.FretePort;
import com.exemplo.pedidos.application.port.out.PedidoRepositoryPort;
import com.exemplo.pedidos.domain.Frete;
import com.exemplo.pedidos.domain.Pedido;
import com.exemplo.pedidos.domain.PedidoCriadoEvento;
import com.exemplo.pedidos.domain.StatusPedido;

public class RegistrarPedidoCriadoService implements RegistrarPedidoCriadoUseCase {

    private final PedidoRepositoryPort repository;
    private final FretePort fretePort;

    public RegistrarPedidoCriadoService(PedidoRepositoryPort repository, FretePort fretePort) {
        this.repository = repository;
        this.fretePort = fretePort;
    }

    @Override
    public Resultado registrar(PedidoCriadoEvento evento) {
        if (evento.total().signum() < 0) {
            throw new EventoInvalidoException("total negativo: " + evento.total());
        }
        Pedido pedido = new Pedido(evento.pedidoId().toString(), evento.clienteId(), evento.itens(),
                evento.total(), StatusPedido.CRIADO, evento.ocorridoEm());
        Frete frete = fretePort.cotar(evento.clienteId());
        pedido.definirFrete(frete.valor(), frete.estimado());
        try {
            repository.inserir(pedido);
        } catch (PedidoJaExisteException e) {
            return Resultado.JA_EXISTIA;
        }
        return Resultado.GRAVADO;
    }
}
