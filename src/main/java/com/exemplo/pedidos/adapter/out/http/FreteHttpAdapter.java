package com.exemplo.pedidos.adapter.out.http;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.exemplo.pedidos.application.port.out.FretePort;
import com.exemplo.pedidos.domain.Frete;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;

@Profile("!cloud")
@Component
public class FreteHttpAdapter implements FretePort {

    private static final Logger log = LoggerFactory.getLogger(FreteHttpAdapter.class);

    private final RestClient restClient;

    public FreteHttpAdapter(@Qualifier("freteRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    @CircuitBreaker(name = "frete", fallbackMethod = "fretePadrao")
    public Frete cotar(String clienteId) {
        CotacaoFrete cotacao = restClient.get()
                .uri("/frete?clienteId={clienteId}", clienteId)
                .retrieve()
                .body(CotacaoFrete.class);
        return new Frete(cotacao.valor(), cotacao.prazoDias(), false);
    }

    private Frete fretePadrao(String clienteId, Throwable erro) {
        log.warn("[frete] cotacao indisponivel para {} ({}: {}), usando frete padrao",
                clienteId, erro.getClass().getSimpleName(), erro.getMessage());
        return Frete.PADRAO;
    }
}
