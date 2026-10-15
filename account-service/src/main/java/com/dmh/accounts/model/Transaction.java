package com.dmh.accounts.model;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

/**
 * Movimiento de dinero de una cuenta. Una transferencia genera dos movimientos: TRANSFER_OUT en la
 * cuenta de origen y TRANSFER_IN en la de destino.
 * <p>
 * Los datos de la contraparte (CVU, alias, nombre) y de la tarjeta se guardan como copia al momento
 * de la operacion: el comprobante tiene que seguir mostrando lo que fue, aunque despues cambie el
 * alias o se elimine la tarjeta.
 */
@Entity
@Table(name = "transactions", indexes = @Index(name = "idx_transactions_account_dated", columnList = "account_id, dated"))
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransactionType type;

    /** Siempre positivo: el signo lo da el tipo. */
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(length = 140)
    private String description;

    @Column(nullable = false)
    private Instant dated;

    @Column(name = "card_id")
    private Long cardId;

    @Column(name = "card_last_four", length = 4)
    private String cardLastFour;

    @Column(name = "counterpart_account_id")
    private Long counterpartAccountId;

    @Column(name = "counterpart_cvu", length = 22)
    private String counterpartCvu;

    @Column(name = "counterpart_alias", length = 100)
    private String counterpartAlias;

    @Column(name = "counterpart_name", length = 130)
    private String counterpartName;

    protected Transaction() {
        // requerido por JPA
    }

    private Transaction(Long accountId, TransactionType type, BigDecimal amount, String description, Instant dated) {
        this.accountId = accountId;
        this.type = type;
        this.amount = amount;
        this.description = description;
        this.dated = dated;
    }

    public static Transaction deposit(Long accountId, BigDecimal amount, Card card, Instant dated) {
        Transaction transaction = new Transaction(accountId, TransactionType.DEPOSIT, amount,
                "Ingreso desde tarjeta terminada en " + card.getLastFour(), dated);
        transaction.cardId = card.getId();
        transaction.cardLastFour = card.getLastFour();
        return transaction;
    }

    public static Transaction transfer(TransactionType type, Account own, Account counterpart, String counterpartName,
            BigDecimal amount, String description, Instant dated) {
        Transaction transaction = new Transaction(own.getId(), type, amount, description, dated);
        transaction.counterpartAccountId = counterpart.getId();
        transaction.counterpartCvu = counterpart.getCvu();
        transaction.counterpartAlias = counterpart.getAlias();
        transaction.counterpartName = counterpartName;
        return transaction;
    }

    public Long getId() {
        return id;
    }

    public Long getAccountId() {
        return accountId;
    }

    public TransactionType getType() {
        return type;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getDescription() {
        return description;
    }

    public Instant getDated() {
        return dated;
    }

    public Long getCardId() {
        return cardId;
    }

    public String getCardLastFour() {
        return cardLastFour;
    }

    public Long getCounterpartAccountId() {
        return counterpartAccountId;
    }

    public String getCounterpartCvu() {
        return counterpartCvu;
    }

    public String getCounterpartAlias() {
        return counterpartAlias;
    }

    public String getCounterpartName() {
        return counterpartName;
    }
}
