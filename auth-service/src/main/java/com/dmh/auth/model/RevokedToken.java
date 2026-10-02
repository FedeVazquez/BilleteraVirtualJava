package com.dmh.auth.model;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

/**
 * Token invalidado por un logout (blacklist). Se guarda el identificador del JWT (claim "jti"), no el
 * token completo, y su vencimiento para poder borrar la fila cuando el token ya no sirve igual.
 */
@Entity
@Table(name = "revoked_tokens", indexes = @Index(name = "idx_revoked_tokens_expires_at", columnList = "expires_at"))
public class RevokedToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 36)
    private String jti;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    protected RevokedToken() {
        // requerido por JPA
    }

    public RevokedToken(String jti, Instant expiresAt) {
        this.jti = jti;
        this.expiresAt = expiresAt;
    }

    public Long getId() {
        return id;
    }

    public String getJti() {
        return jti;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }
}
