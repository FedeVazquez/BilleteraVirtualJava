package com.dmh.accounts.model;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "accounts")
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    @Column(nullable = false, unique = true, length = 22)
    private String cvu;

    @Column(nullable = false, unique = true, length = 100)
    private String alias;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal balance = BigDecimal.ZERO;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Account() {
        // requerido por JPA
    }

    public Account(Long userId, String cvu, String alias) {
        this.userId = userId;
        this.cvu = cvu;
        this.alias = alias;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public String getCvu() {
        return cvu;
    }

    public String getAlias() {
        return alias;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
