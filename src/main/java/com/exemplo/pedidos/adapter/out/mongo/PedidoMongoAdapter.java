package com.exemplo.pedidos.adapter.out.mongo;

import java.util.Optional;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import com.exemplo.pedidos.application.Pagina;
import com.exemplo.pedidos.application.Paginacao;
import com.exemplo.pedidos.application.erro.PedidoJaExisteException;
import com.exemplo.pedidos.application.port.out.PedidoRepositoryPort;
import com.exemplo.pedidos.domain.Pedido;

@Component
class PedidoMongoAdapter implements PedidoRepositoryPort {

    private final PedidoMongoRepository repository;

    PedidoMongoAdapter(PedidoMongoRepository repository) {
        this.repository = repository;
    }

    @Override
    public Pedido salvar(Pedido pedido) {
        PedidoDocument gravado = repository.save(PedidoDocument.de(pedido));
        if (pedido.getId() == null) {
            pedido.atribuirId(gravado.id());
        }
        return pedido;
    }

    @Override
    public void inserir(Pedido pedido) {
        try {
            repository.insert(PedidoDocument.de(pedido));
        } catch (DuplicateKeyException e) {
            throw new PedidoJaExisteException(pedido.getId());
        }
    }

    @Override
    public Optional<Pedido> buscarPorId(String id) {
        return repository.findById(id).map(PedidoDocument::paraDominio);
    }

    @Override
    public Pagina<Pedido> listar(Paginacao paginacao) {
        Sort ordem = Sort.by(paginacao.ordens().stream()
                .map(o -> o.ascendente() ? Sort.Order.asc(o.campo()) : Sort.Order.desc(o.campo()))
                .toList());
        Page<PedidoDocument> pagina = repository.findAll(
                PageRequest.of(paginacao.numero(), paginacao.tamanho(), ordem));
        return new Pagina<>(pagina.map(PedidoDocument::paraDominio).getContent(),
                pagina.getNumber(), pagina.getSize(), pagina.getTotalElements());
    }

    @Override
    public boolean existe(String id) {
        return repository.existsById(id);
    }

    @Override
    public void remover(String id) {
        repository.deleteById(id);
    }
}
