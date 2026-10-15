package com.dmh.accounts.model;

import java.time.Instant;
import java.time.YearMonth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/**
 * Tarjeta de debito o credito asociada a una cuenta.
 * <p>
 * Nunca se guarda el numero completo ni el codigo de seguridad: solo los ultimos 4 digitos (para
 * mostrarla) y una huella HMAC del numero, que permite detectar si la misma tarjeta ya esta
 * asociada a otra cuenta sin poder reconstruir el numero.
 */
@Entity
@Table(name = "cards", indexes = @Index(name = "idx_cards_account_id", columnList = "account_id"))
public class Card {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Column(name = "number_fingerprint", nullable = false, unique = true, length = 64)
    private String numberFingerprint;

    @Column(name = "last_four", nullable = false, length = 4)
    private String lastFour;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CardBrand brand;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private CardType type;

    @Column(name = "holder_name", nullable = false, length = 60)
    private String holderName;

    @Column(name = "expiration_month", nullable = false)
    private int expirationMonth;

    @Column(name = "expiration_year", nullable = false)
    private int expirationYear;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Card() {
        // requerido por JPA
    }

    public Card(Long accountId, String numberFingerprint, String lastFour, CardBrand brand, CardType type,
            String holderName, YearMonth expiration) {
        this.accountId = accountId;
        this.numberFingerprint = numberFingerprint;
        this.lastFour = lastFour;
        this.brand = brand;
        this.type = type;
        this.holderName = holderName;
        this.expirationMonth = expiration.getMonthValue();
        this.expirationYear = expiration.getYear();
    }

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    public YearMonth getExpiration() {
        return YearMonth.of(expirationYear, expirationMonth);
    }

    public boolean isExpired() {
        return getExpiration().isBefore(YearMonth.now());
    }

    public Long getId() {
        return id;
    }

    public Long getAccountId() {
        return accountId;
    }

    public String getNumberFingerprint() {
        return numberFingerprint;
    }

    public String getLastFour() {
        return lastFour;
    }

    public CardBrand getBrand() {
        return brand;
    }

    public CardType getType() {
        return type;
    }

    public String getHolderName() {
        return holderName;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
