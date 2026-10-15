package com.dmh.accounts.service;

import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dmh.accounts.dto.ActivityFilter;
import com.dmh.accounts.dto.TransactionResponse;
import com.dmh.accounts.dto.TransactionResponse.Direction;
import com.dmh.accounts.exception.ResourceNotFoundException;
import com.dmh.accounts.exception.ValidationException;
import com.dmh.accounts.model.Account;
import com.dmh.accounts.model.Transaction;
import com.dmh.accounts.model.TransactionType;
import com.dmh.accounts.repository.TransactionRepository;
import com.dmh.accounts.security.AccountAccess;

/** Actividad de la cuenta: todos los movimientos y el detalle de cada uno. */
@Service
public class ActivityService {

    private static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("dated"), Sort.Order.desc("id"));

    private final TransactionRepository transactionRepository;
    private final AccountAccess accountAccess;
    private final ZoneId zone;

    public ActivityService(TransactionRepository transactionRepository, AccountAccess accountAccess,
            @Value("${app.timezone}") String timezone) {
        this.transactionRepository = transactionRepository;
        this.accountAccess = accountAccess;
        this.zone = ZoneId.of(timezone);
    }

    /** Todos los movimientos de la cuenta que cumplen los filtros, del mas reciente al mas antiguo. */
    @Transactional(readOnly = true)
    public List<TransactionResponse> activity(Long accountId, ActivityFilter filter) {
        Account account = accountAccess.requireAccess(accountId);
        validate(filter);
        return transactionRepository.findAll(specification(account.getId(), filter), NEWEST_FIRST).stream()
                .map(TransactionResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public TransactionResponse detail(Long accountId, Long transactionId) {
        return TransactionResponse.from(findOwned(accountId, transactionId));
    }

    /** Movimiento de la cuenta, o 404 si no existe o es de otra cuenta. */
    @Transactional(readOnly = true)
    public Transaction findOwned(Long accountId, Long transactionId) {
        Account account = accountAccess.requireAccess(accountId);
        return transactionRepository.findByIdAndAccountId(transactionId, account.getId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No existe la actividad " + transactionId + " en la cuenta " + accountId));
    }

    private static void validate(ActivityFilter filter) {
        if (filter.from() != null && filter.to() != null && filter.from().isAfter(filter.to())) {
            throw new ValidationException("La fecha desde no puede ser posterior a la fecha hasta");
        }
        if (filter.minAmount() != null && filter.maxAmount() != null
                && filter.minAmount().compareTo(filter.maxAmount()) > 0) {
            throw new ValidationException("El monto mínimo no puede ser mayor al monto máximo");
        }
        if ((filter.minAmount() != null && filter.minAmount().signum() < 0)
                || (filter.maxAmount() != null && filter.maxAmount().signum() < 0)) {
            throw new ValidationException("Los montos del filtro no pueden ser negativos");
        }
    }

    /** Arma la consulta con los filtros presentes (Criteria API de JPA mediante Specifications). */
    private Specification<Transaction> specification(Long accountId, ActivityFilter filter) {
        return (root, query, cb) -> {
            var predicates = new ArrayList<jakarta.persistence.criteria.Predicate>();
            predicates.add(cb.equal(root.get("accountId"), accountId));
            if (filter.direction() != null) {
                List<TransactionType> types = java.util.Arrays.stream(TransactionType.values())
                        .filter(type -> type.isIncome() == (filter.direction() == Direction.INCOME))
                        .toList();
                predicates.add(root.get("type").in(types));
            }
            if (filter.from() != null) {
                Instant start = filter.from().atStartOfDay(zone).toInstant();
                predicates.add(cb.greaterThanOrEqualTo(root.get("dated"), start));
            }
            if (filter.to() != null) {
                Instant endExclusive = filter.to().plusDays(1).atStartOfDay(zone).toInstant();
                predicates.add(cb.lessThan(root.get("dated"), endExclusive));
            }
            if (filter.minAmount() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("amount"), filter.minAmount()));
            }
            if (filter.maxAmount() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("amount"), filter.maxAmount()));
            }
            return cb.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
    }
}
