package com.exemplo.pedidos.frete;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("frete")
public record FreteProperties(String url, Duration timeoutConexao, Duration timeoutLeitura) {
}