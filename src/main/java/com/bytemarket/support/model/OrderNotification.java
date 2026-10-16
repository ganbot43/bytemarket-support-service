package com.bytemarket.support.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Marca de "este aviso ya se mandó".
 *
 * Existe por una razón concreta: Kafka entrega AL MENOS una vez. Si este
 * servicio procesa un evento y muere antes de confirmar el offset, al
 * arrancar lo vuelve a leer. Sin esta tabla, el cliente recibiría dos veces
 * el mismo correo —o diez, si el reinicio se repite—, y un cliente que
 * recibe diez avisos de lo mismo deja de leerlos.
 *
 * Es el mismo recurso que StockService usa en catalog con related_order_id:
 * un consumidor de cola solo es correcto si es idempotente.
 *
 * La clave única es (order_code, estado), no el pedido solo: un pedido pasa
 * legítimamente por varios estados y cada uno merece su aviso.
 */
@Data
@Entity
@Table(
        name = "order_notifications",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_order_notifications_pedido_estado",
                columnNames = {"order_code", "estado"}))
public class OrderNotification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "order_code", length = 191, nullable = false)
    private String orderCode;

    @Column(length = 30, nullable = false)
    private String estado;

    @Column(columnDefinition = "TEXT")
    private String email;

    /**
     * Si el SMTP aceptó el correo. Se guarda la fila igual cuando falla: así
     * se ve en base que el evento se procesó y por qué no llegó el aviso, en
     * vez de que un fallo de correo parezca un evento nunca recibido.
     */
    @Column(nullable = false)
    private Boolean enviado = false;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
