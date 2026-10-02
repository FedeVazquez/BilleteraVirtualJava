package com.dmh.accounts.service;

import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dmh.accounts.dto.AccountResponse;
import com.dmh.accounts.exception.ResourceNotFoundException;
import com.dmh.accounts.model.Account;
import com.dmh.accounts.repository.AccountRepository;

@Service
public class AccountService {

    private static final int MAX_GENERATION_ATTEMPTS = 10;

    private final AccountRepository accountRepository;
    private final CvuGenerator cvuGenerator;
    private final AliasGenerator aliasGenerator;

    public AccountService(AccountRepository accountRepository, CvuGenerator cvuGenerator,
            AliasGenerator aliasGenerator) {
        this.accountRepository = accountRepository;
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
