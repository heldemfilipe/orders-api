package com.exemplo.pedidos.pedido;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.exemplo.pedidos.erro.PedidoNaoEncontradoException;
import com.exemplo.pedidos.pedido.dto.AtualizarPedidoRequest;
import com.exemplo.pedidos.pedido.dto.ItemRequest;
import com.exemplo.pedidos.pedido.dto.PedidoRequest;
import com.exemplo.pedidos.pedido.dto.PedidoResponse;

@Service
public class PedidoService {

    private final PedidoRepository repository;

    public PedidoService(PedidoRepository repository) {
        this.repository = repository;
    }

    public PedidoResponse criar(PedidoRequest request) {
        List<ItemPedido> itens = converter(request.itens());
        Pedido pedido = Pedido.novo(request.clienteId(), itens, calcularTotal(itens));
        return PedidoResponse.de(repository.save(pedido));
    }

    public PedidoResponse buscar(String id) {
        return PedidoResponse.de(buscarOuFalhar(id));
    }

    public Page<PedidoResponse> listar(Pageable pageable) {
        return repository.findAll(pageable).map(PedidoResponse::de);
    }

    public PedidoResponse atualizar(String id, AtualizarPedidoRequest request) {
        Pedido pedido = buscarOuFalhar(id);
        List<ItemPedido> itens = converter(request.itens());
        pedido.atualizar(itens, calcularTotal(itens), request.status());
        return PedidoResponse.de(repository.save(pedido));
    }

    public void remover(String id) {
        if (!repository.existsById(id)) {
            throw new PedidoNaoEncontradoException(id);
        }
        repository.deleteById(id);
    }

    private Pedido buscarOuFalhar(String id) {
        return repository.findById(id).orElseThrow(() -> new PedidoNaoEncontradoException(id));
    }

    private List<ItemPedido> converter(List<ItemRequest> itens) {
        return itens.stream()
                .map(i -> new ItemPedido(i.produto(), i.quantidade(), i.precoUnitario()))
                .toList();
    }

    private BigDecimal calcularTotal(List<ItemPedido> itens) {
        return itens.stream()
                .map(i -> i.precoUnitario().multiply(BigDecimal.valueOf(i.quantidade())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
