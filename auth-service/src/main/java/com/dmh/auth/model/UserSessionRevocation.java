package com.dmh.auth.model;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * "Cierre de todas las sesiones" de un usuario: cualquier token suyo emitido antes de
 * {@code revokedBefore} deja de ser valido. Se registra cuando el usuario cambia su contraseña.
 */
@Entity
@Table(name = "user_session_revocations")
public class UserSessionRevocation {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "revoked_before", nullable = false)
    private Instant revokedBefore;

    protected UserSessionRevocation() {
        // requerido por JPA
    }

    public UserSessionRevocation(Long userId, Instant revokedBefore) {
        this.userId = userId;
        this.revokedBefore = revokedBefore;
    }

    public Long getUserId() {
        return userId;
    }

    public Instant getRevokedBefore() {
        return revokedBefore;
    }
}
