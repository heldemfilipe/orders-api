package com.exemplo.pedidos.arquitetura;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(packages = "com.exemplo.pedidos", importOptions = ImportOption.DoNotIncludeTests.class)
class ArquiteturaTest {

    // o dominio so enxerga o JDK e ele mesmo: nada de Spring, Jackson, Kafka, Mongo ou validacao
    @ArchTest
    static final ArchRule dominioSoDependeDoJdk = classes()
            .that().resideInAPackage("com.exemplo.pedidos.domain..")
            .should().onlyDependOnClassesThat()
            .resideInAnyPackage("java..", "com.exemplo.pedidos.domain..");

    @ArchTest
    static final ArchRule dominioNaoImportaSpring = noClasses()
            .that().resideInAPackage("com.exemplo.pedidos.domain..")
            .should().dependOnClassesThat().resideInAPackage("org.springframework..");

    // a aplicacao conhece o dominio e as proprias portas; nunca um adapter nem um framework
    @ArchTest
    static final ArchRule aplicacaoSoDependeDoDominioEDelaMesma = classes()
            .that().resideInAPackage("com.exemplo.pedidos.application..")
            .should().onlyDependOnClassesThat()
            .resideInAnyPackage("java..", "com.exemplo.pedidos.domain..", "com.exemplo.pedidos.application..");

    @ArchTest
    static final ArchRule aplicacaoNaoImportaAdapter = noClasses()
            .that().resideInAPackage("com.exemplo.pedidos.application..")
            .should().dependOnClassesThat().resideInAPackage("com.exemplo.pedidos.adapter..");

    // quem chega (rest, kafka) e quem sai (mongo, kafka, http) nao se conhecem: conversam pela aplicacao
    @ArchTest
    static final ArchRule entradaNaoConheceSaida = noClasses()
            .that().resideInAPackage("com.exemplo.pedidos.adapter.in..")
            .should().dependOnClassesThat().resideInAPackage("com.exemplo.pedidos.adapter.out..");

    @ArchTest
    static final ArchRule saidaNaoConheceEntrada = noClasses()
            .that().resideInAPackage("com.exemplo.pedidos.adapter.out..")
            .should().dependOnClassesThat().resideInAPackage("com.exemplo.pedidos.adapter.in..");

    // cada tecnologia fica presa ao seu adapter
    @ArchTest
    static final ArchRule mongoSoNoAdapterDeMongo = noClasses()
            .that().resideOutsideOfPackage("com.exemplo.pedidos.adapter.out.mongo..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("com.mongodb..", "org.springframework.data.mongodb..");

    @ArchTest
    static final ArchRule kafkaSoNosAdaptersDeKafka = noClasses()
            .that().resideOutsideOfPackages(
                    "com.exemplo.pedidos.adapter.kafka..",
                    "com.exemplo.pedidos.adapter.in.kafka..",
                    "com.exemplo.pedidos.adapter.out.kafka..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("org.apache.kafka..", "org.springframework.kafka..");

    @ArchTest
    static final ArchRule resilience4jSoNoAdapterHttp = noClasses()
            .that().resideOutsideOfPackage("com.exemplo.pedidos.adapter.out.http..")
            .should().dependOnClassesThat().resideInAPackage("io.github.resilience4j..");
}
