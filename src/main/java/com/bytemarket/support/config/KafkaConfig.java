package com.bytemarket.support.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Declara el topic de pedidos también desde aquí, aunque este servicio solo
 * consuma.
 *
 * Es la misma razón por la que RabbitConfig está duplicado en order y en
 * catalog: "los dos servicios declaran lo mismo para que el orden de
 * arranque no importe". Con Kafka la consecuencia de no hacerlo es peor y se
 * vio en pruebas:
 *
 *   1. support-service arrancó primero y se suscribió a un topic que aún no
 *      existía.
 *   2. El broker lo auto-creó al vuelo con su valor por defecto: UNA
 *      partición.
 *   3. order-service arrancó después y, al declararlo con tres, Kafka le
 *      añadió las dos que faltaban.
 *   4. El consumidor seguía con la metadata vieja: solo tenía asignada la
 *      partición 0, y los eventos que caían en la 1 y la 2 no los leía
 *      nadie. Ningún error en los logs, simplemente no llegaban los correos.
 *
 * Declarándolo en los dos lados, el primero que arranque lo crea con las
 * tres particiones y el segundo se encuentra lo que esperaba. Declarar un
 * topic que ya existe igual no da error.
 *
 * El número de particiones TIENE que coincidir con el de order-service.
 */
@Configuration
public class KafkaConfig {

    public static final String TOPIC_PEDIDOS = "bytemarket.pedidos";

    @Bean
    NewTopic topicPedidos() {
        return TopicBuilder.name(TOPIC_PEDIDOS)
                .partitions(3)
                .replicas(1)
                .build();
    }
}
