package com.bytemarket.support.service;

import com.bytemarket.support.messaging.PedidoEvento;
import com.bytemarket.support.model.OrderNotification;
import com.bytemarket.support.repository.IOrderNotificationRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Las reglas de a quién se avisa y de qué, cuando un pedido cambia.
 *
 * Vive en support-service y no en order-service a propósito: order-service
 * sabe de pedidos, no de correos. Si el aviso se enviara desde allí, añadir
 * mañana un SMS o un WhatsApp obligaría a tocar el servicio que cobra —y una
 * caída del SMTP podría tumbar un checkout—. Publicando el hecho en Kafka,
 * order-service se desentiende y aquí se decide qué hacer con él.
 *
 * Fuera del listener de Kafka por lo mismo que ComplaintService está fuera
 * del controlador: estas reglas se prueban sin levantar Spring ni un broker.
 */
@Service
@RequiredArgsConstructor
public class OrderNotificationService {

    private static final Logger log = LoggerFactory.getLogger(OrderNotificationService.class);

    /**
     * Qué se le dice al cliente en cada estado.
     *
     * Los estados que NO están aquí no generan correo, y eso es una decisión,
     * no un olvido:
     *
     *  · "pending" lo cubre el evento de creación, con su propio texto.
     *  · "voucher_sent" lo acaba de hacer el cliente —acaba de subir su
     *    comprobante—, así que un correo avisándole de lo que él mismo hizo
     *    hace dos segundos es ruido.
     */
    private static final Map<String, String[]> MENSAJES = Map.of(
            "payment_validated", new String[]{
                    "Confirmamos tu pago",
                    "Validamos el pago de tu pedido. Ya entra en preparación."},
            "in_preparation", new String[]{
                    "Estamos preparando tu pedido",
                    "Tu pedido está siendo preparado. Te avisamos en cuanto salga."},
            "delivered", new String[]{
                    "Tu pedido fue entregado",
                    "Tu pedido ya fue entregado. ¡Gracias por comprar en ByteMarket!"},
            "cancelled", new String[]{
                    "Tu pedido fue cancelado",
                    "Tu pedido fue cancelado. Si no esperabas esto, escríbenos y lo revisamos."});

    private static final String[] MENSAJE_CREADO = {
            "Recibimos tu pedido",
            "Recibimos tu pedido y lo estamos revisando. Te avisaremos al validar el pago."};

    private final IOrderNotificationRepository notificationRepository;
    private final EmailService emailService;

    /**
     * Procesa un evento del topic de pedidos.
     *
     * No lanza por un evento que no toca avisar ni por un correo rechazado:
     * el evento se da por consumido igual. Lo único que debe propagar es un
     * fallo transitorio —la base no responde—, para que Kafka lo reintente.
     */
    @Transactional
    public void procesar(PedidoEvento evento) {
        if (evento == null || evento.orderCode() == null) {
            log.warn("Evento de pedido sin código, se ignora");
            return;
        }

        String[] mensaje = mensajeDe(evento);
        if (mensaje == null) {
            log.debug("Pedido {}: el estado '{}' no genera aviso al cliente",
                    evento.orderCode(), evento.estado());
            return;
        }

        // La marca de idempotencia. Se mira ANTES de mandar: Kafka reentrega
        // al menos una vez y sin esto el cliente recibiría el aviso repetido.
        String clave = clave(evento);
        if (notificationRepository.existsByOrderCodeAndEstado(evento.orderCode(), clave)) {
            log.debug("Pedido {}: el aviso de '{}' ya se había mandado, se ignora",
                    evento.orderCode(), clave);
            return;
        }

        boolean enviado = emailService.sendOrderStatusEmail(
                evento.customerEmail(),
                evento.customerName(),
                evento.orderCode(),
                mensaje[0],
                mensaje[1],
                evento.total());

        OrderNotification registro = new OrderNotification();
        registro.setOrderCode(evento.orderCode());
        registro.setEstado(clave);
        registro.setEmail(evento.customerEmail());
        registro.setEnviado(enviado);
        registro.setCreatedAt(LocalDateTime.now());
        notificationRepository.save(registro);

        if (enviado) {
            log.info("Pedido {}: avisado al cliente del estado '{}'", evento.orderCode(), clave);
        } else {
            // Queda la fila con enviado=false: no se reintenta en bucle, pero
            // en base se ve que el evento llegó y el correo no salió.
            log.warn("Pedido {}: no se pudo avisar del estado '{}'", evento.orderCode(), clave);
        }
    }

    /** El asunto y el cuerpo que toca, o null si ese evento no se avisa. */
    private String[] mensajeDe(PedidoEvento evento) {
        if (PedidoEvento.CREADO.equals(evento.tipo())) return MENSAJE_CREADO;
        if (!PedidoEvento.ESTADO_CAMBIADO.equals(evento.tipo())) return null;
        return MENSAJES.get(evento.estado());
    }

    /**
     * La creación se marca con "__creado" y no con su estado ("pending").
     *
     * Si usara "pending", un pedido creado y luego devuelto a pending a mano
     * desde el panel no volvería a avisar nunca, porque la marca ya estaría
     * puesta por la creación. Son dos avisos distintos y llevan marca
     * distinta.
     */
    private String clave(PedidoEvento evento) {
        return PedidoEvento.CREADO.equals(evento.tipo()) ? "__creado" : evento.estado();
    }
}
