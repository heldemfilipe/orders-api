package com.exemplo.pedidos.kafka;

import java.util.List;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.dao.DuplicateKeyException;

import com.exemplo.pedidos.pedido.ItemPedido;
import com.exemplo.pedidos.pedido.Pedido;
import com.exemplo.pedidos.pedido.PedidoRepository;
import com.exemplo.pedidos.pedido.StatusPedido;
import com.exemplo.pedidos.frete.CotacaoFrete;
import com.exemplo.pedidos.frete.FreteClient;

@Component
public class PedidoCriadoPersistenciaListener {

    private static final Logger log = LoggerFactory.getLogger(PedidoCriadoPersistenciaListener.class);

    private final PedidoRepository repository;
    private final FreteClient freteClient;
    private final String instancia;

    public PedidoCriadoPersistenciaListener(PedidoRepository repository, FreteClient freteClient,
                                            @Value("${server.port:8080}") String instancia) {
        this.repository = repository;
        this.freteClient = freteClient;
        this.instancia = instancia;
    }

    @KafkaListener(topics = KafkaTopicConfig.PEDIDOS_CRIADOS, groupId = "persistencia")
    public void consumir(ConsumerRecord<String, PedidoCriado> registro) {
        PedidoCriado evento = registro.value();
        if (evento.total().signum() < 0) {
            throw new EventoInvalidoException("total negativo: " + evento.total());
        }
        List<ItemPedido> itens = evento.itens().stream()
                .map(i -> new ItemPedido(i.produto(), i.quantidade(), i.precoUnitario()))
                .toList();

        Pedido pedido = new Pedido(evento.pedidoId().toString(), evento.clienteId(), itens,
                evento.total(), StatusPedido.CRIADO, evento.ocorridoEm());
        CotacaoFrete cotacao = freteClient.cotar(evento.clienteId());
        pedido.definirFrete(cotacao.valor(), false);
        try {
            repository.insert(pedido);
        } catch (DuplicateKeyException e) {
            log.warn("[persistencia][instancia {}] pedido {} ja existe (evento {} reentregue), ignorando",
                    instancia, pedido.getId(), evento.eventId());
            return;
        }

        log.info("[persistencia][instancia {}] pedido {} gravado (cliente {}, particao {}, offset {})",
                instancia, pedido.getId(), evento.clienteId(), registro.partition(), registro.offset());    }
}