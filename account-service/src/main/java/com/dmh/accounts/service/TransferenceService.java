package com.dmh.accounts.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dmh.accounts.dto.TransactionResponse;
import com.dmh.accounts.dto.TransferenceRequest;
import com.dmh.accounts.exception.ValidationException;
import com.dmh.accounts.model.Account;
import com.dmh.accounts.model.Card;
import com.dmh.accounts.model.Transaction;
import com.dmh.accounts.repository.TransactionRepository;
import com.dmh.accounts.security.AccountAccess;

/** Operaciones que mueven dinero. */
@Service
public class TransferenceService {

    private static final Logger log = LoggerFactory.getLogger(TransferenceService.class);

    private final AccountAccess accountAccess;
    private final CardService cardService;
    private final TransactionRepository transactionRepository;
    private final Clock clock;

    public TransferenceService(AccountAccess accountAccess, CardService cardService,
            TransactionRepository transactionRepository, Clock clock) {
        this.accountAccess = accountAccess;
        this.cardService = cardService;
        this.transactionRepository = transactionRepository;
        this.clock = clock;
    }

    /**
     * Ingresa dinero a la billetera desde una tarjeta propia. La cuenta se bloquea durante la
     * operacion para que dos ingresos simultaneos no se pisen el saldo.
     *
     * @throws com.dmh.accounts.exception.ResourceNotFoundException si la tarjeta no es de la cuenta (404)
     * @throws ValidationException si la tarjeta esta vencida (400)
     */
    @Transactional
    public TransactionResponse deposit(Long accountId, TransferenceRequest request) {
        Account account = accountAccess.requireAccessForUpdate(accountId);
        Card card = cardService.findOwnedCard(account.getId(), request.cardId());
        if (card.isExpired()) {
            throw new ValidationException("La tarjeta está vencida");
        }
        BigDecimal amount = request.amount().setScale(2);
        account.credit(amount);
        Transaction deposit = transactionRepository.save(
                Transaction.deposit(account.getId(), amount, card, Instant.now(clock)));
        log.info("Ingreso {}: cuenta {} +{} desde tarjeta {}", deposit.getId(), account.getId(), amount, card.getId());
        return TransactionResponse.from(deposit);
    }
}
