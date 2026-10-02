package com.dmh.users.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dmh.users.event.PasswordResetRequestedEvent;
import com.dmh.users.exception.ValidationException;
import com.dmh.users.model.User;
import com.dmh.users.repository.UserRepository;

/**
 * Recupero de contraseña: se envia por mail un link con un token aleatorio de un solo uso.
 * En la base solo se guarda el hash del token, igual que con las contraseñas.
 */
@Service
public class PasswordResetService {

    private static final int TOKEN_BYTES = 32;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher events;
    private final Duration tokenTtl;
    private final SecureRandom random = new SecureRandom();

    public PasswordResetService(UserRepository userRepository, PasswordEncoder passwordEncoder,
            ApplicationEventPublisher events,
            @Value("${app.password-reset.token-ttl-minutes}") long tokenTtlMinutes) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.events = events;
        this.tokenTtl = Duration.ofMinutes(tokenTtlMinutes);
    }

    /**
     * Si el email no pertenece a ningun usuario no hace nada y no informa error: la respuesta es
     * siempre la misma para no revelar que emails estan registrados.
     */
    @Transactional
    public void request(String email) {
        userRepository.findByEmail(UserService.normalizeEmail(email)).ifPresent(user -> {
            byte[] bytes = new byte[TOKEN_BYTES];
            random.nextBytes(bytes);
            String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
            user.startPasswordReset(hash(token), Instant.now().plus(tokenTtl));
            events.publishEvent(new PasswordResetRequestedEvent(user.getEmail(), user.getFirstName(), token));
        });
    }

    /**
     * @return el id del usuario, para que auth-service pueda cerrar sus sesiones abiertas
     */
    @Transactional
    public Long confirm(String token, String newPassword) {
        User user = userRepository.findByResetTokenHash(hash(token))
                .orElseThrow(() -> new ValidationException("El enlace no es válido o ya fue utilizado"));
        if (Instant.now().isAfter(user.getResetTokenExpiresAt())) {
            throw new ValidationException("El enlace venció. Solicite uno nuevo.");
        }
        user.changePassword(passwordEncoder.encode(newPassword));
        return user.getId();
    }

    static String hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
