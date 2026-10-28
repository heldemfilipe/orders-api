package com.exemplo.pedidos.frete;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class FreteClient {

    private final RestClient restClient;

    public FreteClient(@Qualifier("freteRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    public CotacaoFrete cotar(String clienteId) {
        return restClient.get()
                .uri("/frete?clienteId={clienteId}", clienteId)
                .retrieve()
                .body(CotacaoFrete.class);
    }
}