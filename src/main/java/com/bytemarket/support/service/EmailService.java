package com.bytemarket.support.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender javaMailSender;
    private final TemplateEngine templateEngine;

    @Value("${spring.mail.username}")
    private String senderEmail;

    /**
     * @return true solo si el correo salió de verdad. El panel lo muestra al
     *         encargado, así que no puede decir "enviado" cuando el servidor
     *         SMTP lo rechazó: creería que el cliente ya está avisado.
     */
    public boolean sendComplaintResolutionEmail(String toEmail, String customerName, String complaintCode, String resolutionText) {
        if (toEmail == null || toEmail.isBlank()) {
            log.warn("No se pudo enviar correo para el reclamo {} porque el cliente no dejó email.", complaintCode);
            return false;
        }

        try {
            Context context = new Context();
            context.setVariable("customerName", customerName != null ? customerName : "Cliente");
            context.setVariable("complaintCode", complaintCode);
            context.setVariable("resolutionText", resolutionText);

            String process = templateEngine.process("complaint-resolution", context);

            MimeMessage mimeMessage = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, "utf-8");

            helper.setText(process, true); // true = isHtml
            helper.setTo(toEmail);
            helper.setSubject("Resolución de su Reclamo - " + complaintCode);
            helper.setFrom("Bytemarket <" + senderEmail + ">");

            javaMailSender.send(mimeMessage);
            log.info("Correo de resolución enviado a {}", toEmail);
            return true;
        } catch (Exception e) {
            // No se propaga: el reclamo ya quedó resuelto en base y perder el
            // correo no debe deshacer eso. Queda en el log y el panel lo sabe
            // por el false.
            log.error("Error crítico al enviar el correo de resolución a {}: ", toEmail, e);
            return false;
        }
    }
}
