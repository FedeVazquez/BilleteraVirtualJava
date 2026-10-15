package com.dmh.accounts.service;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dmh.accounts.dto.AccountResponse;
import com.dmh.accounts.dto.TransactionResponse;
import com.dmh.accounts.exception.ConflictException;
import com.dmh.accounts.exception.ResourceNotFoundException;
import com.dmh.accounts.model.Account;
import com.dmh.accounts.repository.AccountRepository;
import com.dmh.accounts.repository.TransactionRepository;
import com.dmh.accounts.security.AccountAccess;

@Service
public class AccountService {

    private static final int MAX_GENERATION_ATTEMPTS = 10;
    private static final int LAST_TRANSACTIONS = 5;

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final AccountAccess accountAccess;
    private final CvuGenerator cvuGenerator;
    private final AliasGenerator aliasGenerator;

    public AccountService(AccountRepository accountRepository, TransactionRepository transactionRepository,
            AccountAccess accountAccess, CvuGenerator cvuGenerator, AliasGenerator aliasGenerator) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.accountAccess = accountAccess;
        this.cvuGenerator = cvuGenerator;
        this.aliasGenerator = aliasGenerator;
    }

    /**
     * Crea la cuenta digital de un usuario con CVU y alias autogenerados.
     * Es idempotente: si el usuario ya tiene cuenta, devuelve la existente.
     */
    @Transactional
    public AccountResponse createForUser(Long userId) {
        Optional<Account> existing = accountRepository.findByUserId(userId);
        if (existing.isPresent()) {
            return AccountResponse.from(existing.get());
        }
        String cvu = generateUnique(cvuGenerator::generate, accountRepository::existsByCvu, "CVU");
        String alias = generateUnique(aliasGenerator::generate, accountRepository::existsByAlias, "alias");
        Account account = accountRepository.save(new Account(userId, cvu, alias));
        return AccountResponse.from(account);
    }

    @Transactional(readOnly = true)
    public AccountResponse getByUserId(Long userId) {
        return accountRepository.findByUserId(userId)
                .map(AccountResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("No existe una cuenta para el usuario " + userId));
    }

    /** Datos de la cuenta, incluido el saldo disponible. */
    @Transactional(readOnly = true)
    public AccountResponse getById(Long accountId) {
        return AccountResponse.from(accountAccess.requireAccess(accountId));
    }

    @Transactional
    public AccountResponse updateAlias(Long accountId, String newAlias) {
        Account account = accountAccess.requireAccess(accountId);
        String alias = newAlias.trim().toLowerCase(Locale.ROOT);
        accountRepository.findByAlias(alias)
                .filter(other -> !other.getId().equals(account.getId()))
                .ifPresent(other -> {
                    throw new ConflictException("El alias " + alias + " ya está en uso");
                });
        account.changeAlias(alias);
        return AccountResponse.from(account);
    }

    /** Ultimos 5 movimientos, del mas reciente al mas antiguo. */
    @Transactional(readOnly = true)
    public List<TransactionResponse> lastTransactions(Long accountId) {
        Account account = accountAccess.requireAccess(accountId);
        return transactionRepository
                .findByAccountIdOrderByDatedDescIdDesc(account.getId(), PageRequest.of(0, LAST_TRANSACTIONS))
                .stream()
                .map(TransactionResponse::from)
                .toList();
    }

    private static String generateUnique(Supplier<String> generator, Predicate<String> alreadyUsed, String what) {
        for (int attempt = 0; attempt < MAX_GENERATION_ATTEMPTS; attempt++) {
            String candidate = generator.get();
            if (!alreadyUsed.test(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("No se pudo generar un " + what + " unico");
    }
}
