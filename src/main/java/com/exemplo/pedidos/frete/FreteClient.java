package com.exemplo.pedidos.frete;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;

@Component
public class FreteClient {

    private static final Logger log = LoggerFactory.getLogger(FreteClient.class);

    private final RestClient restClient;

    public FreteClient(@Qualifier("freteRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    @CircuitBreaker(name = "frete", fallbackMethod = "fretePadrao")
    public Frete cotar(String clienteId) {
        CotacaoFrete cotacao = restClient.get()
                .uri("/frete?clienteId={clienteId}", clienteId)
                .retrieve()
                .body(CotacaoFrete.class);
        return Frete.de(cotacao);
    }

    private Frete fretePadrao(String clienteId, Throwable erro) {
        log.warn("[frete] cotacao indisponivel para {} ({}: {}), usando frete padrao",
                clienteId, erro.getClass().getSimpleName(), erro.getMessage());
        return Frete.PADRAO;
    }
}