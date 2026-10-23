package com.exemplo.pedidos.pedido;

import java.net.URI;
import jakarta.validation.Valid;

import org.springframework.data.domain.Page;
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

import com.exemplo.pedidos.pedido.dto.AtualizarPedidoRequest;
import com.exemplo.pedidos.pedido.dto.PedidoRequest;
import com.exemplo.pedidos.pedido.dto.PedidoResponse;

@RestController
@RequestMapping("/pedidos")
public class PedidoController {

    private final PedidoService service;

    public PedidoController(PedidoService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<PedidoResponse> criar(@Valid @RequestBody PedidoRequest request) {
        PedidoResponse criado = service.criar(request);
        URI local = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(criado.id()).toUri();
        return ResponseEntity.created(local).body(criado);
    }

    @GetMapping("/{id}")
    public PedidoResponse buscar(@PathVariable String id) {
        return service.buscar(id);
    }

    @GetMapping
    public PagedModel<PedidoResponse> listar(
            @PageableDefault(size = 20, sort = "criadoEm", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<PedidoResponse> pagina = service.listar(pageable);
        return new PagedModel<>(pagina);
    }

    @PutMapping("/{id}")
    public PedidoResponse atualizar(@PathVariable String id, @Valid @RequestBody AtualizarPedidoRequest request) {
        return service.atualizar(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> remover(@PathVariable String id) {
        service.remover(id);
        return ResponseEntity.noContent().build();
    }
}
