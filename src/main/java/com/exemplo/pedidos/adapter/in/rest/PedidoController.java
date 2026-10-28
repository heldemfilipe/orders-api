package com.exemplo.pedidos.adapter.in.rest;

import java.net.URI;
import java.util.List;

import jakarta.validation.Valid;

import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.exemplo.pedidos.adapter.in.rest.dto.AtualizarPedidoRequest;
import com.exemplo.pedidos.adapter.in.rest.dto.ItemRequest;
import com.exemplo.pedidos.adapter.in.rest.dto.PedidoRequest;
import com.exemplo.pedidos.adapter.in.rest.dto.PedidoResponse;
import com.exemplo.pedidos.application.Pagina;
import com.exemplo.pedidos.application.Paginacao;
import com.exemplo.pedidos.application.port.in.AtualizarPedidoUseCase;
import com.exemplo.pedidos.application.port.in.BuscarPedidoUseCase;
import com.exemplo.pedidos.application.port.in.CriarPedidoUseCase;
import com.exemplo.pedidos.application.port.in.ListarPedidosUseCase;
import com.exemplo.pedidos.application.port.in.RemoverPedidoUseCase;
import com.exemplo.pedidos.domain.ItemPedido;
import com.exemplo.pedidos.domain.Pedido;

@RestController
@RequestMapping("/pedidos")
public class PedidoController {

    private final CriarPedidoUseCase criarPedido;
    private final BuscarPedidoUseCase buscarPedido;
    private final ListarPedidosUseCase listarPedidos;
    private final AtualizarPedidoUseCase atualizarPedido;
    private final RemoverPedidoUseCase removerPedido;

    public PedidoController(CriarPedidoUseCase criarPedido, BuscarPedidoUseCase buscarPedido,
                            ListarPedidosUseCase listarPedidos,
                            AtualizarPedidoUseCase atualizarPedido, RemoverPedidoUseCase removerPedido) {
        this.criarPedido = criarPedido;
        this.buscarPedido = buscarPedido;
        this.listarPedidos = listarPedidos;
        this.atualizarPedido = atualizarPedido;
        this.removerPedido = removerPedido;
    }

    @PostMapping
    public ResponseEntity<PedidoResponse> criar(@Valid @RequestBody PedidoRequest request) {
        Pedido criado = criarPedido.criar(new CriarPedidoUseCase.Comando(request.clienteId(), itens(request.itens())));
        URI local = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(criado.getId()).toUri();
        return ResponseEntity.created(local).body(PedidoResponse.de(criado));
    }

    @GetMapping("/{id}")
    public PedidoResponse buscar(@PathVariable String id) {
        return PedidoResponse.de(buscarPedido.buscar(id));
    }

    @GetMapping
    public PagedModel<PedidoResponse> listar(
            @PageableDefault(size = 20, sort = "criadoEm", direction = Sort.Direction.DESC) Pageable pageable) {
        List<Paginacao.Ordem> ordens = pageable.getSort().stream()
                .map(o -> new Paginacao.Ordem(o.getProperty(), o.isAscending()))
                .toList();
        Pagina<PedidoResponse> pagina = listarPedidos.listar(
                new Paginacao(pageable.getPageNumber(), pageable.getPageSize(), ordens))
                .mapear(PedidoResponse::de);
        return new PagedModel<>(new PageImpl<>(pagina.conteudo(),
                PageRequest.of(pagina.numero(), pagina.tamanho()), pagina.totalElementos()));
    }

    @PutMapping("/{id}")
    public PedidoResponse atualizar(@PathVariable String id, @Valid @RequestBody AtualizarPedidoRequest request) {
        return PedidoResponse.de(atualizarPedido.atualizar(
                new AtualizarPedidoUseCase.Comando(id, itens(request.itens()), request.status())));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> remover(@PathVariable String id) {
        removerPedido.remover(id);
        return ResponseEntity.noContent().build();
    }

    private static List<ItemPedido> itens(List<ItemRequest> itens) {
        return itens.stream().map(ItemRequest::paraItem).toList();
    }
}
