package com.bytemarket.support.messaging;

import java.time.LocalDateTime;

/**
 * Lo que llega por el topic bytemarket.pedidos.
 *
 * Es una COPIA del contrato que publica order-service, no una clase
 * compartida, y eso es a propósito: el mismo criterio que ya se siguió con
 * la cola de stock ("no ata los dos servicios a la misma clase"). Si el
 * esquema viviera en un jar común, order-service no podría añadir un campo
 * sin recompilar y redesplegar este servicio, y entonces no serían dos
 * servicios independientes sino uno partido en dos.
 *
 * Por eso aquí solo están los campos que ESTE consumidor necesita. Jackson
 * ignora los que sobren, así que order-service puede añadir campos nuevos
 * sin romper nada; lo único que no puede hacer es quitar los de abajo.
 */
public record PedidoEvento(
        String tipo,
        String orderCode,
        Long orderId,
        String estado,
        String estadoAnterior,
        String customerName,
        String customerEmail,
        Double total,
        LocalDateTime ocurridoEn) {

    public static final String CREADO = "PEDIDO_CREADO";
    public static final String ESTADO_CAMBIADO = "PEDIDO_ESTADO_CAMBIADO";
}
