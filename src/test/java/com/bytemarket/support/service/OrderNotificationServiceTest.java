package com.bytemarket.support.service;

import com.bytemarket.support.messaging.PedidoEvento;
import com.bytemarket.support.model.OrderNotification;
import com.bytemarket.support.repository.IOrderNotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Reglas del aviso al cliente cuando su pedido cambia.
 *
 * Lo que más importa aquí es la idempotencia: Kafka entrega al menos una
 * vez, así que estos tests son la garantía de que un evento repetido no
 * manda dos correos. Sin ellos, el fallo solo se vería en producción y en
 * forma de cliente molesto.
 */
class OrderNotificationServiceTest {

    private IOrderNotificationRepository notificationRepository;
    private EmailService emailService;
    private OrderNotificationService service;

    @BeforeEach
    void setUp() {
        notificationRepository = mock(IOrderNotificationRepository.class);
        emailService = mock(EmailService.class);
        service = new OrderNotificationService(notificationRepository, emailService);

        when(emailService.sendOrderStatusEmail(
                anyString(), anyString(), anyString(), anyString(), anyString(), anyDouble()))
                .thenReturn(true);
    }

    private PedidoEvento evento(String tipo, String estado) {
        return new PedidoEvento(tipo, "BM-20261015-4821", 7L, estado, null,
                "Ana Quispe", "ana@ejemplo.com", 249.90, LocalDateTime.now());
    }

    @Test
    @DisplayName("un pedido nuevo avisa al cliente de que se recibió")
    void avisaAlCrear() {
        service.procesar(evento(PedidoEvento.CREADO, "pending"));

        verify(emailService).sendOrderStatusEmail(
                eq("ana@ejemplo.com"), eq("Ana Quispe"), eq("BM-20261015-4821"),
                eq("Recibimos tu pedido"), anyString(), eq(249.90));
        verify(notificationRepository).save(any(OrderNotification.class));
    }

    @Test
    @DisplayName("el cambio a un estado avisable manda el correo de ese estado")
    void avisaAlCambiarDeEstado() {
        service.procesar(evento(PedidoEvento.ESTADO_CAMBIADO, "delivered"));

        ArgumentCaptor<String> titulo = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendOrderStatusEmail(
                anyString(), anyString(), anyString(), titulo.capture(), anyString(), anyDouble());
        assertEquals("Tu pedido fue entregado", titulo.getValue());
    }

    @Test
    @DisplayName("el mismo evento dos veces manda UN solo correo")
    void esIdempotente() {
        // Primera entrega: no hay marca, se envía y se guarda.
        service.procesar(evento(PedidoEvento.ESTADO_CAMBIADO, "delivered"));

        // Kafka reentrega: ahora la marca ya está en base.
        when(notificationRepository.existsByOrderCodeAndEstado("BM-20261015-4821", "delivered"))
                .thenReturn(true);
        service.procesar(evento(PedidoEvento.ESTADO_CAMBIADO, "delivered"));

        verify(emailService, times(1)).sendOrderStatusEmail(
                anyString(), anyString(), anyString(), anyString(), anyString(), anyDouble());
    }

    @Test
    @DisplayName("subir el comprobante no genera correo: lo acaba de hacer el cliente")
    void noAvisaDeVoucherSent() {
        service.procesar(evento(PedidoEvento.ESTADO_CAMBIADO, "voucher_sent"));

        verifyNoInteractions(emailService);
        verifyNoInteractions(notificationRepository);
    }

    @Test
    @DisplayName("la creación y el estado 'pending' llevan marcas distintas")
    void creacionYPendingNoSePisan() {
        service.procesar(evento(PedidoEvento.CREADO, "pending"));

        ArgumentCaptor<OrderNotification> guardado = ArgumentCaptor.forClass(OrderNotification.class);
        verify(notificationRepository).save(guardado.capture());
        // Si se marcara como "pending", devolver el pedido a pending a mano
        // desde el panel no volvería a avisar nunca.
        assertEquals("__creado", guardado.getValue().getEstado());
    }

    @Test
    @DisplayName("si el SMTP rechaza el correo se guarda la fila con enviado=false")
    void registraElFalloDeCorreo() {
        when(emailService.sendOrderStatusEmail(
                anyString(), anyString(), anyString(), anyString(), anyString(), anyDouble()))
                .thenReturn(false);

        service.procesar(evento(PedidoEvento.ESTADO_CAMBIADO, "delivered"));

        ArgumentCaptor<OrderNotification> guardado = ArgumentCaptor.forClass(OrderNotification.class);
        verify(notificationRepository).save(guardado.capture());
        assertFalse(guardado.getValue().getEnviado());
    }

    @Test
    @DisplayName("un pedido sin correo no revienta el consumidor")
    void sinCorreoNoFalla() {
        PedidoEvento sinEmail = new PedidoEvento(PedidoEvento.CREADO, "BM-20261015-0001", 1L,
                "pending", null, "Ana Quispe", null, 10.0, LocalDateTime.now());

        // El EmailService real devuelve false cuando no hay destinatario; lo
        // que se comprueba aquí es que procesar() no lanza por ello: lanzar
        // haría que Kafka reintentara un evento que nunca va a poder enviarse.
        when(emailService.sendOrderStatusEmail(
                any(), anyString(), anyString(), anyString(), anyString(), anyDouble()))
                .thenReturn(false);

        assertDoesNotThrow(() -> service.procesar(sinEmail));
        verify(notificationRepository).save(any(OrderNotification.class));
    }

    @Test
    @DisplayName("un evento sin código de pedido se ignora sin tocar nada")
    void eventoSinCodigoSeIgnora() {
        PedidoEvento roto = new PedidoEvento(PedidoEvento.CREADO, null, null, "pending", null,
                "Ana", "ana@ejemplo.com", 10.0, LocalDateTime.now());

        assertDoesNotThrow(() -> service.procesar(roto));
        verifyNoInteractions(emailService);
        verifyNoInteractions(notificationRepository);
    }
}
