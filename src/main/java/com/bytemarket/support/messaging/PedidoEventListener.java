package com.bytemarket.support.messaging;

import com.bytemarket.support.config.KafkaConfig;
import com.bytemarket.support.service.OrderNotificationService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Escucha el topic de pedidos y delega.
 *
 * Aquí no hay ni una regla de negocio: solo recibe el evento y se lo pasa a
 * OrderNotificationService. Es la misma separación que ya existe entre
 * StockListener y StockService en catalog —la cola es un detalle de
 * transporte, y las reglas no deben saber de dónde vino el mensaje—.
 *
 * El nombre del topic sale de la KafkaConfig de ESTE servicio, no de la de
 * order-service: compartir esa clase ataría los dos servicios al mismo jar.
 */
@Component
@RequiredArgsConstructor
public class PedidoEventListener {

    private static final Logger log = LoggerFactory.getLogger(PedidoEventListener.class);

    private final OrderNotificationService orderNotificationService;

    @KafkaListener(topics = KafkaConfig.TOPIC_PEDIDOS, groupId = "${spring.kafka.consumer.group-id}")
    public void alRecibir(PedidoEvento evento) {
        try {
            orderNotificationService.procesar(evento);
        } catch (Exception e) {
            // Se propaga para que Kafka NO confirme el offset y lo reintente.
            // Es seguro reintentar porque procesar() es idempotente.
            log.error("Fallo procesando el evento de pedido {}, se reintentará: {}",
                    evento == null ? "?" : evento.orderCode(), e.getMessage());
            throw e;
        }
    }
}
