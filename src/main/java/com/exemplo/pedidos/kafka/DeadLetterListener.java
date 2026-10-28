package com.exemplo.pedidos.kafka;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.stereotype.Component;

import com.exemplo.pedidos.falha.MensagemFalha;
import com.exemplo.pedidos.falha.MensagemFalhaRepository;

@Profile("!cloud")
@Component
public class DeadLetterListener {

    private static final Logger log = LoggerFactory.getLogger(DeadLetterListener.class);

    private final MensagemFalhaRepository repository;

    public DeadLetterListener(MensagemFalhaRepository repository) {
        this.repository = repository;
    }

    @KafkaListener(topics = KafkaErroConfig.PEDIDOS_CRIADOS_DLT, groupId = "dlt",
            properties = "value.deserializer=org.apache.kafka.common.serialization.StringDeserializer")
    public void registrar(ConsumerRecord<String, String> registro) {
        Headers cabecalhos = registro.headers();
        MensagemFalha falha = new MensagemFalha(null,
                texto(cabecalhos, KafkaHeaders.DLT_ORIGINAL_TOPIC),
                inteiro(cabecalhos, KafkaHeaders.DLT_ORIGINAL_PARTITION),
                longo(cabecalhos, KafkaHeaders.DLT_ORIGINAL_OFFSET),
                texto(cabecalhos, KafkaHeaders.DLT_ORIGINAL_CONSUMER_GROUP),
                registro.key(),
                registro.value(),
                texto(cabecalhos, KafkaHeaders.DLT_EXCEPTION_CAUSE_FQCN),
                texto(cabecalhos, KafkaHeaders.DLT_EXCEPTION_MESSAGE),
                Instant.now());
        repository.save(falha);

        log.warn("[dlt] mensagem de {} (particao {}, offset {}, group {}) registrada: {}",
                falha.topicoOriginal(), falha.particaoOriginal(), falha.offsetOriginal(),
                falha.grupoOriginal(), falha.excecao());
    }

    private static String texto(Headers cabecalhos, String nome) {
        Header h = cabecalhos.lastHeader(nome);
        return h == null ? null : new String(h.value(), StandardCharsets.UTF_8);
    }

    private static int inteiro(Headers cabecalhos, String nome) {
        Header h = cabecalhos.lastHeader(nome);
        return h == null ? -1 : ByteBuffer.wrap(h.value()).getInt();
    }

    private static long longo(Headers cabecalhos, String nome) {
        Header h = cabecalhos.lastHeader(nome);
        return h == null ? -1L : ByteBuffer.wrap(h.value()).getLong();
    }
}
