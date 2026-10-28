package com.exemplo.pedidos.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import com.exemplo.pedidos.application.port.out.AuditoriaPort;
import com.exemplo.pedidos.application.port.out.EventoPublisherPort;
import com.exemplo.pedidos.application.port.out.FalhaPort;
import com.exemplo.pedidos.application.port.out.FretePort;
import com.exemplo.pedidos.application.port.out.PedidoRepositoryPort;
import com.exemplo.pedidos.application.service.AuditoriaService;
import com.exemplo.pedidos.application.service.FalhaService;
import com.exemplo.pedidos.application.service.PedidoEventoService;
import com.exemplo.pedidos.application.service.PedidoService;
import com.exemplo.pedidos.application.service.RegistrarPedidoCriadoService;

// os casos de uso nao conhecem o Spring: e aqui, e so aqui, que eles viram beans
@Configuration
public class CasosDeUsoConfig {

    @Bean
    PedidoService pedidoService(PedidoRepositoryPort repository) {
        return new PedidoService(repository);
    }

    @Profile("!cloud")
    @Bean
    PedidoEventoService pedidoEventoService(EventoPublisherPort publisher) {
        return new PedidoEventoService(publisher);
    }

    @Profile("!cloud")
    @Bean
    RegistrarPedidoCriadoService registrarPedidoCriadoService(PedidoRepositoryPort repository, FretePort fretePort) {
        return new RegistrarPedidoCriadoService(repository, fretePort);
    }

    @Profile("!cloud")
    @Bean
    AuditoriaService auditoriaService(AuditoriaPort auditoriaPort) {
        return new AuditoriaService(auditoriaPort);
    }

    @Profile("!cloud")
    @Bean
    FalhaService falhaService(FalhaPort falhaPort) {
        return new FalhaService(falhaPort);
    }
}
