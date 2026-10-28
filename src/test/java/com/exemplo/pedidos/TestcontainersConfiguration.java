package com.exemplo.pedidos;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.mongodb.MongoDBContainer;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.MountableFile;

@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    MongoDBContainer mongoDbContainer() {
        // mongo:8 se recusa a iniciar no kernel 7.0.0-azure dos runners do GitHub (SERVER-121912)
        return new MongoDBContainer("mongo:7.0");
    }

    @Bean
    @ServiceConnection
    KafkaContainer kafkaContainer() {
        return new KafkaContainer("apache/kafka:4.2.1");
    }
    @Bean
    GenericContainer<?> freteContainer() {
        return new GenericContainer<>("wiremock/wiremock:3.13.2")
                .withExposedPorts(8080)
                .withCopyFileToContainer(MountableFile.forHostPath("wiremock/mappings"), "/home/wiremock/mappings")
                .waitingFor(Wait.forHttp("/__admin/health").forStatusCode(200));
    }

    @Bean
    DynamicPropertyRegistrar freteProperties(@Qualifier("freteContainer") GenericContainer<?> freteContainer) {
        return registry -> registry.add("frete.url",
                () -> "http://" + freteContainer.getHost() + ":" + freteContainer.getMappedPort(8080));
    }
}
