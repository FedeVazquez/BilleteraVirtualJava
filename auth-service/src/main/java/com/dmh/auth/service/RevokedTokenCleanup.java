package com.dmh.auth.service;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.dmh.auth.repository.RevokedTokenRepository;

/**
 * Un token vencido ya es rechazado por su fecha de expiracion, asi que no hace falta seguir
 * guardandolo en la blacklist: esta tarea borra esas filas una vez por hora.
 */
@Component
public class RevokedTokenCleanup {

    private static final Logger log = LoggerFactory.getLogger(RevokedTokenCleanup.class);

    private final RevokedTokenRepository revokedTokenRepository;

    public RevokedTokenCleanup(RevokedTokenRepository revokedTokenRepository) {
        this.revokedTokenRepository = revokedTokenRepository;
    }

    @Scheduled(fixedDelayString = "PT1H", initialDelayString = "PT1M")
    public void deleteExpired() {
        long deleted = revokedTokenRepository.deleteByExpiresAtBefore(Instant.now());
        if (deleted > 0) {
            log.info("Blacklist de tokens: se borraron {} tokens ya vencidos", deleted);
        }
    }
}
