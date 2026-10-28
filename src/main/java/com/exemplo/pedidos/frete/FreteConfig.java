package com.exemplo.pedidos.frete;

import java.net.http.HttpClient;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(FreteProperties.class)
public class FreteConfig {

    @Bean
    RestClient freteRestClient(FreteProperties propriedades) {
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(propriedades.timeoutConexao())
                .build();
        JdkClientHttpRequestFactory fabrica = new JdkClientHttpRequestFactory(httpClient);
        fabrica.setReadTimeout(propriedades.timeoutLeitura());

        return RestClient.builder()
                .baseUrl(propriedades.url())
                .requestFactory(fabrica)
                .build();
    }
}