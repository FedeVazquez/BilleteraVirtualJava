package com.dmh.users.service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionalEventListener;

import com.dmh.users.event.PasswordResetRequestedEvent;
import com.dmh.users.event.VerificationCodeIssuedEvent;

/**
 * Envia los mails. Escucha eventos y actua recien cuando la transaccion que los publico hizo commit:
 * asi nunca sale un mail con un codigo o token que no llego a guardarse en la base.
 */
@Service
public class MailService {

    private static final Logger log = LoggerFactory.getLogger(MailService.class);

    private final JavaMailSender mailSender;
    private final String from;
    private final String resetLinkBaseUrl;
    private final long codeTtlMinutes;
    private final long resetTtlMinutes;

    public MailService(JavaMailSender mailSender,
            @Value("${app.mail.from}") String from,
            @Value("${app.password-reset.link-base-url}") String resetLinkBaseUrl,
            @Value("${app.email-verification.code-ttl-minutes}") long codeTtlMinutes,
            @Value("${app.password-reset.token-ttl-minutes}") long resetTtlMinutes) {
        this.mailSender = mailSender;
        this.from = from;
        this.resetLinkBaseUrl = resetLinkBaseUrl;
        this.codeTtlMinutes = codeTtlMinutes;
        this.resetTtlMinutes = resetTtlMinutes;
    }

    @TransactionalEventListener(fallbackExecution = true)
    public void onVerificationCodeIssued(VerificationCodeIssuedEvent event) {
        send(event.email(), "Digital Money House - Verificá tu email",
                "Hola " + event.firstName() + ",\n\n"
                        + "Tu código de verificación es: " + event.code() + "\n\n"
                        + "Vence en " + codeTtlMinutes + " minutos.\n\n"
                        + "Si no creaste una cuenta en Digital Money House, ignorá este mensaje.");
    }

    @TransactionalEventListener(fallbackExecution = true)
    public void onPasswordResetRequested(PasswordResetRequestedEvent event) {
        String link = resetLinkBaseUrl + "?token=" + URLEncoder.encode(event.token(), StandardCharsets.UTF_8);
        send(event.email(), "Digital Money House - Recuperá tu contraseña",
                "Hola " + event.firstName() + ",\n\n"
                        + "Para elegir una nueva contraseña ingresá al siguiente enlace:\n\n" + link + "\n\n"
                        + "El enlace vence en " + resetTtlMinutes + " minutos y solo puede usarse una vez.\n\n"
                        + "Si no pediste recuperar tu contraseña, ignorá este mensaje.");
    }

    private void send(String to, String subject, String text) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(text);
        try {
            mailSender.send(message);
        } catch (MailException e) {
            // Un fallo del servidor de correo no debe romper el registro: el usuario puede pedir el reenvio.
            log.warn("No se pudo enviar el mail \"{}\": {}", subject, e.getMessage());
        }
    }
}
