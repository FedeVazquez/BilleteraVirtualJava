package com.dmh.accounts.model;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * Operacion de dinero ya procesada con una clave de idempotencia. Si el cliente reenvia la misma
 * operacion (doble clic, reintento por timeout) con la misma clave, se devuelve el resultado original
 * en lugar de mover el dinero otra vez.
 */
@Entity
@Table(name = "idempotency_records",
        uniqueConstraints = @UniqueConstraint(name = "uk_idempotency_account_key", columnNames = { "account_id", "idempotency_key" }))
public class IdempotencyRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Column(name = "idempotency_key", nullable = false, length = 64)
    private String idempotencyKey;

    @Column(name = "transaction_id", nullable = false)
    private Long transactionId;

    /** Huella de los datos de la operacion: la misma clave con otros datos es un error del cliente. */
    @Column(name = "request_fingerprint", nullable = false, length = 64)
    private String requestFingerprint;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected IdempotencyRecord() {
        // requerido por JPA
    }

    public IdempotencyRecord(Long accountId, String key, String requestFingerprint, Long transactionId,
            Instant createdAt) {
        this.accountId = accountId;
        this.idempotencyKey = key;
        this.requestFingerprint = requestFingerprint;
        this.transactionId = transactionId;
        this.createdAt = createdAt;
    }

    public String getRequestFingerprint() {
        return requestFingerprint;
    }

    public Long getTransactionId() {
        return transactionId;
    }
}
