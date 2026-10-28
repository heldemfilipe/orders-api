package com.exemplo.pedidos.adapter.out.mongo;

import org.springframework.stereotype.Component;

import com.exemplo.pedidos.application.RegistroFalha;
import com.exemplo.pedidos.application.port.out.FalhaPort;

@Component
class FalhaMongoAdapter implements FalhaPort {

    private final MensagemFalhaRepository repository;

    FalhaMongoAdapter(MensagemFalhaRepository repository) {
        this.repository = repository;
    }

    @Override
    public void salvar(RegistroFalha falha) {
        repository.save(new MensagemFalha(null, falha.topicoOriginal(), falha.particaoOriginal(),
                falha.offsetOriginal(), falha.grupoOriginal(), falha.chave(), falha.payload(),
                falha.excecao(), falha.motivo(), falha.recebidoEm()));
    }
}
