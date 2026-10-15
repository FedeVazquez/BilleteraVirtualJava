package com.dmh.accounts.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.dmh.accounts.model.Transaction;
import com.dmh.accounts.model.TransactionType;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Un movimiento. {@code amount} siempre es positivo; {@code direction} indica si fue un ingreso o un egreso.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TransactionResponse(Long id, Long accountId, TransactionType type, Direction direction,
        BigDecimal amount, String description, Instant date, String cardLastFour, Counterpart counterpart) {

    public enum Direction {
        INCOME,
        EXPENSE
    }

    /** La otra cuenta de una transferencia: el origen si es un ingreso, el destino si es un egreso. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Counterpart(Long accountId, String cvu, String alias, String name) {
    }

    public static TransactionResponse from(Transaction transaction) {
        Counterpart counterpart = transaction.getCounterpartAccountId() == null ? null
                : new Counterpart(transaction.getCounterpartAccountId(), transaction.getCounterpartCvu(),
                        transaction.getCounterpartAlias(), transaction.getCounterpartName());
        return new TransactionResponse(transaction.getId(), transaction.getAccountId(), transaction.getType(),
                transaction.getType().isIncome() ? Direction.INCOME : Direction.EXPENSE, transaction.getAmount(),
                transaction.getDescription(), transaction.getDated(), transaction.getCardLastFour(), counterpart);
    }
}
