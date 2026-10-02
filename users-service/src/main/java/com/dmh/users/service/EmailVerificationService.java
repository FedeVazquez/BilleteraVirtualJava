package com.dmh.users.service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dmh.users.event.VerificationCodeIssuedEvent;
import com.dmh.users.exception.ResourceNotFoundException;
import com.dmh.users.exception.ValidationException;
import com.dmh.users.model.User;
import com.dmh.users.repository.UserRepository;

/**
 * Verificacion del email con un codigo de 6 digitos que se envia por correo al registrarse.
 */
@Service
public class EmailVerificationService {

    private final UserRepository userRepository;
    private final ApplicationEventPublisher events;
    private final Duration codeTtl;
    private final int maxAttempts;
    private final SecureRandom random = new SecureRandom();

    public EmailVerificationService(UserRepository userRepository, ApplicationEventPublisher events,
            @Value("${app.email-verification.code-ttl-minutes}") long codeTtlMinutes,
            @Value("${app.email-verification.max-attempts}") int maxAttempts) {
        this.userRepository = userRepository;
        this.events = events;
        this.codeTtl = Duration.ofMinutes(codeTtlMinutes);
        this.maxAttempts = maxAttempts;
    }

    /**
     * Asigna un codigo nuevo al usuario y publica el evento que dispara el mail.
     * Debe llamarse dentro de la transaccion que guarda al usuario.
     */
    public void issueCode(User user) {
        String code = String.format("%06d", random.nextInt(1_000_000));
        user.startEmailVerification(code, Instant.now().plus(codeTtl));
        events.publishEvent(new VerificationCodeIssuedEvent(user.getEmail(), user.getFirstName(), code));
    }

    // noRollbackFor: el intento fallido tiene que quedar contado aunque se responda con error.
    @Transactional(noRollbackFor = ValidationException.class)
    public void confirm(String email, String code) {
        User user = findByEmail(email);
        if (user.isEmailVerified()) {
            return;
        }
        if (user.getVerificationCode() == null || Instant.now().isAfter(user.getVerificationCodeExpiresAt())) {
            throw new ValidationException("El código venció. Solicite uno nuevo.");
        }
        if (user.getVerificationAttempts() >= maxAttempts) {
            throw new ValidationException("Se superó la cantidad de intentos. Solicite un código nuevo.");
        }
        if (!user.getVerificationCode().equals(code)) {
            user.registerFailedVerificationAttempt();
            throw new ValidationException("El código de verificación es incorrecto");
        }
        user.markEmailVerified();
    }

    @Transactional
    public void resend(String email) {
        User user = findByEmail(email);
        if (user.isEmailVerified()) {
            throw new ValidationException("El email ya fue verificado");
        }
        issueCode(user);
    }

    private User findByEmail(String email) {
        return userRepository.findByEmail(UserService.normalizeEmail(email))
                .orElseThrow(() -> new ResourceNotFoundException("No existe un usuario con ese email"));
    }
}
