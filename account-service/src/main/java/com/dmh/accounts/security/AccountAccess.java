package com.dmh.accounts.security;

import java.util.Optional;
import java.util.function.Function;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import com.dmh.accounts.exception.ResourceNotFoundException;
import com.dmh.accounts.model.Account;
import com.dmh.accounts.repository.AccountRepository;

/**
 * Regla de acceso a una cuenta: solo la puede usar su dueño (el usuario del token) o un administrador.
 * <p>
 * A un usuario comun se le responde 403 tanto si la cuenta es ajena como si no existe, para no
 * revelar que ids de cuenta existen. Solo un administrador recibe 404.
 */
@Component
public class AccountAccess {

    private static final String ROLE_ADMIN = "ROLE_ADMIN";

    private final AccountRepository accountRepository;

    public AccountAccess(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    public Account requireAccess(Long accountId) {
        return check(accountId, accountRepository::findById);
    }

    /** Igual que {@link #requireAccess}, pero bloquea la fila para modificar el saldo. */
    public Account requireAccessForUpdate(Long accountId) {
        return check(accountId, accountRepository::findByIdForUpdate);
    }

    private Account check(Long accountId, Function<Long, Optional<Account>> finder) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean admin = authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> ROLE_ADMIN.equals(authority.getAuthority()));
        Optional<Account> account = finder.apply(accountId);
        if (admin) {
            return account.orElseThrow(() -> new ResourceNotFoundException("No existe la cuenta " + accountId));
        }
        Long userId = authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser user
                ? user.id() : null;
        return account.filter(found -> found.getUserId().equals(userId))
                .orElseThrow(() -> new AccessDeniedException("No tiene permisos sobre la cuenta " + accountId));
    }
}
