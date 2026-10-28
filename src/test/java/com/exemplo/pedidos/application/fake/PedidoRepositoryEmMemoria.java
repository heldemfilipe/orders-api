package com.exemplo.pedidos.application.fake;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import com.exemplo.pedidos.application.Pagina;
import com.exemplo.pedidos.application.Paginacao;
import com.exemplo.pedidos.application.erro.PedidoJaExisteException;
import com.exemplo.pedidos.application.port.out.PedidoRepositoryPort;
import com.exemplo.pedidos.domain.Pedido;

// substitui o MongoDB nos testes dos casos de uso: o dominio e a aplicacao nao percebem a troca
public class PedidoRepositoryEmMemoria implements PedidoRepositoryPort {

    private final Map<String, Pedido> pedidos = new LinkedHashMap<>();
    private final AtomicInteger sequencia = new AtomicInteger();

    @Override
    public Pedido salvar(Pedido pedido) {
        if (pedido.getId() == null) {
            pedido.atribuirId("mem-" + sequencia.incrementAndGet());
        }
        pedidos.put(pedido.getId(), pedido);
        return pedido;
    }

    @Override
    public void inserir(Pedido pedido) {
        if (pedidos.containsKey(pedido.getId())) {
            throw new PedidoJaExisteException(pedido.getId());
        }
        pedidos.put(pedido.getId(), pedido);
    }

    @Override
    public Optional<Pedido> buscarPorId(String id) {
        return Optional.ofNullable(pedidos.get(id));
    }

    @Override
    public Pagina<Pedido> listar(Paginacao paginacao) {
        List<Pedido> todos = new ArrayList<>(pedidos.values());
        int de = Math.min(paginacao.numero() * paginacao.tamanho(), todos.size());
        int ate = Math.min(de + paginacao.tamanho(), todos.size());
        return new Pagina<>(todos.subList(de, ate), paginacao.numero(), paginacao.tamanho(), todos.size());
    }

    @Override
    public boolean existe(String id) {
        return pedidos.containsKey(id);
    }

    @Override
    public void remover(String id) {
        pedidos.remove(id);
    }

    public List<Pedido> todos() {
        return List.copyOf(pedidos.values());
    }
}
