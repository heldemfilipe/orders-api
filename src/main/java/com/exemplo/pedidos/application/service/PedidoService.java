package com.exemplo.pedidos.application.service;

import com.exemplo.pedidos.application.Pagina;
import com.exemplo.pedidos.application.Paginacao;
import com.exemplo.pedidos.application.erro.PedidoNaoEncontradoException;
import com.exemplo.pedidos.application.port.in.AtualizarPedidoUseCase;
import com.exemplo.pedidos.application.port.in.BuscarPedidoUseCase;
import com.exemplo.pedidos.application.port.in.CriarPedidoUseCase;
import com.exemplo.pedidos.application.port.in.ListarPedidosUseCase;
import com.exemplo.pedidos.application.port.in.RemoverPedidoUseCase;
import com.exemplo.pedidos.application.port.out.PedidoRepositoryPort;
import com.exemplo.pedidos.domain.Pedido;

public class PedidoService implements CriarPedidoUseCase, BuscarPedidoUseCase, ListarPedidosUseCase,
        AtualizarPedidoUseCase, RemoverPedidoUseCase {

    private final PedidoRepositoryPort repository;

    public PedidoService(PedidoRepositoryPort repository) {
        this.repository = repository;
    }

    @Override
    public Pedido criar(CriarPedidoUseCase.Comando comando) {
        return repository.salvar(Pedido.novo(comando.clienteId(), comando.itens()));
    }

    @Override
    public Pedido buscar(String id) {
        return buscarOuFalhar(id);
    }

    @Override
    public Pagina<Pedido> listar(Paginacao paginacao) {
        return repository.listar(paginacao);
    }

    @Override
    public Pedido atualizar(AtualizarPedidoUseCase.Comando comando) {
        Pedido pedido = buscarOuFalhar(comando.id());
        pedido.atualizar(comando.itens(), comando.status());
        return repository.salvar(pedido);
    }

    @Override
    public void remover(String id) {
        if (!repository.existe(id)) {
            throw new PedidoNaoEncontradoException(id);
        }
        repository.remover(id);
    }

    private Pedido buscarOuFalhar(String id) {
        return repository.buscarPorId(id).orElseThrow(() -> new PedidoNaoEncontradoException(id));
    }
}
