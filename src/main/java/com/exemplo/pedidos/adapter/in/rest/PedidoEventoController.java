package com.exemplo.pedidos.adapter.in.rest;

import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.exemplo.pedidos.adapter.in.rest.dto.ItemRequest;
import com.exemplo.pedidos.adapter.in.rest.dto.PedidoAceito;
import com.exemplo.pedidos.adapter.in.rest.dto.PedidoRequest;
import com.exemplo.pedidos.application.port.in.AceitarPedidoUseCase;

import jakarta.validation.Valid;

@Profile("!cloud")
@RestController
@RequestMapping("/pedidos/eventos")
public class PedidoEventoController {

    private final AceitarPedidoUseCase useCase;

    public PedidoEventoController(AceitarPedidoUseCase useCase) {
        this.useCase = useCase;
    }

    @PostMapping
    public ResponseEntity<PedidoAceito> aceitar(@Valid @RequestBody PedidoRequest request) {
        AceitarPedidoUseCase.Aceito aceito = useCase.aceitar(new AceitarPedidoUseCase.Comando(
                request.clienteId(), request.itens().stream().map(ItemRequest::paraItem).toList()));
        return ResponseEntity.accepted().body(new PedidoAceito(aceito.pedidoId(), aceito.eventId()));
    }
}
