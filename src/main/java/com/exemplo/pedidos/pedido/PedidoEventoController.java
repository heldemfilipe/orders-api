package com.exemplo.pedidos.pedido;

import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.exemplo.pedidos.pedido.dto.PedidoAceito;
import com.exemplo.pedidos.pedido.dto.PedidoRequest;

import jakarta.validation.Valid;

@Profile("!cloud")
@RestController
@RequestMapping("/pedidos/eventos")
public class PedidoEventoController {

    private final PedidoEventoService service;

    public PedidoEventoController(PedidoEventoService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<PedidoAceito> aceitar(@Valid @RequestBody PedidoRequest request) {
        return ResponseEntity.accepted().body(service.aceitar(request));
    }
}
