package com.dmh.accounts.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.dmh.accounts.model.Transaction;
import com.dmh.accounts.model.TransactionType;

public interface TransactionRepository extends JpaRepository<Transaction, Long>, JpaSpecificationExecutor<Transaction> {

    /** El desempate por id ordena bien dos movimientos con la misma fecha. */
    List<Transaction> findByAccountIdOrderByDatedDescIdDesc(Long accountId, Pageable pageable);

    Optional<Transaction> findByIdAndAccountId(Long id, Long accountId);

    List<Transaction> findByAccountIdAndTypeOrderByDatedDescIdDesc(Long accountId, TransactionType type,
            Pageable pageable);
}
