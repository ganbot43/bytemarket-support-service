package com.bytemarket.support.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Las plantillas de los correos, renderizadas de verdad.
 *
 * Hacen falta porque un fallo aquí no se nota: EmailService captura la
 * excepción a propósito —perder el correo no debe deshacer el reclamo ya
 * resuelto— así que una expresión mal escrita se convierte en una línea
 * de log que nadie mira y un cliente que nunca recibe nada.
 *
 * Y porque esto no lo pinta un navegador: Outlook de escritorio usa el
 * motor de Word. Por eso se comprueba también que el maquetado siga
 * siendo de tablas con estilos en línea y no vuelva a los divs.
 */
@DisplayName("Correos — las plantillas se renderizan y llevan lo que deben")
class PlantillasDeCorreoTest {

    private SpringTemplateEngine motor;

    @BeforeEach
    void setUp() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding("UTF-8");

        /* El de Spring y no el plano: el plano cae a OGNL para evaluar las
           expresiones y esa librería no está en el classpath —Spring Boot
           trae SpringEL—. Y es el que usa la aplicación de verdad, así que
           la prueba renderiza con el mismo motor que producción. */
        motor = new SpringTemplateEngine();
        motor.setTemplateResolver(resolver);
    }

    private String estado(Object total) {
        Context c = new Context();
        c.setVariable("customerName", "Ana Quispe");
        c.setVariable("orderCode", "BM-20261019-3308");
        c.setVariable("titulo", "Tu pedido está en camino");
        c.setVariable("mensaje", "Lo estamos preparando para enviártelo.");
        c.setVariable("total", total);
        return motor.process("order-status", c);
    }

    @Test
    @DisplayName("el correo de estado sale con sus datos dentro")
    void elCorreoDeEstadoSeRenderiza() {
        String html = estado(new BigDecimal("1234.5"));

        assertTrue(html.contains("Ana Quispe"), "el nombre del cliente");
        assertTrue(html.contains("BM-20261019-3308"), "el código del pedido");
        assertTrue(html.contains("Tu pedido está en camino"), "el título");
        assertTrue(html.contains("Lo estamos preparando"), "el mensaje");
        // Formato peruano: miles con coma y decimales con punto.
        assertTrue(html.contains("S/ 1,234.50"), "el total formateado: " + html.contains("1,234"));
        // Y nada de marcadores sin reemplazar.
        assertFalse(html.contains("th:text"), "quedó un atributo de Thymeleaf sin procesar");
    }

    @Test
    @DisplayName("un pedido sin total no imprime una fila vacía")
    void sinTotalNoSaleLaFila() {
        String html = estado(null);
        assertFalse(html.contains("S/ 0.00"), "con total nulo la fila entera se omite");
        assertTrue(html.contains("BM-20261019-3308"), "pero el resto sigue");
    }

    @Test
    @DisplayName("el correo del reclamo sale con su código y su resolución")
    void elCorreoDelReclamoSeRenderiza() {
        Context c = new Context();
        c.setVariable("customerName", "Luis Fernández");
        c.setVariable("complaintCode", "REC-00042");
        c.setVariable("resolutionText", "Se repuso la batería sin costo.");
        String html = motor.process("complaint-resolution", c);

        assertTrue(html.contains("Luis Fernández"));
        assertTrue(html.contains("REC-00042"));
        assertTrue(html.contains("Se repuso la batería sin costo."));
        assertFalse(html.contains("th:text"));
    }

    /**
     * Sin los comentarios del HTML.
     *
     * La cabecera de cada plantilla explica de qué color venía, y sin
     * quitarlos la prueba del color se caza a sí misma: encuentra el
     * magenta en la línea que cuenta que ya no se usa.
     */
    private static String sinComentarios(String html) {
        return html.replaceAll("(?s)<!--.*?-->", "");
    }

    @Test
    @DisplayName("los dos llevan el color de la marca y ni rastro del magenta")
    void elColorEsElDeLaMarca() {
        for (String html : new String[] {estado(new BigDecimal("10")),
                motor.process("complaint-resolution", new Context())}) {
            String pintado = sinComentarios(html);
            assertTrue(pintado.contains("#071e52"), "el navy del logo");
            assertTrue(pintado.contains("#00aeef"), "el cian de la marca");
            // El magenta de antes no salía en ningún otro sitio del producto.
            assertFalse(pintado.toUpperCase().contains("B01883"), "volvió el magenta");
        }
    }

    @Test
    @DisplayName("siguen maquetados con tablas y estilos en línea")
    void elMaquetadoAguantaOutlook() {
        String html = estado(new BigDecimal("10"));

        // Outlook de escritorio usa el motor de Word: con divs y una hoja
        // en <head>, el correo llega desarmado.
        assertTrue(html.contains("role=\"presentation\""), "maquetado con tablas");
        assertTrue(html.contains("style=\"margin:0"), "estilos en línea");
        assertTrue(html.contains("width=device-width"), "se adapta al móvil");
        // La línea que asoma en la bandeja, junto al asunto.
        assertTrue(html.contains("mso-hide:all"), "la previsualización");
    }
}
