package com.dmh.accounts.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.dmh.accounts.model.Account;

import jakarta.persistence.LockModeType;

public interface AccountRepository extends JpaRepository<Account, Long> {

    Optional<Account> findByUserId(Long userId);

    Optional<Account> findByCvu(String cvu);

    Optional<Account> findByAlias(String alias);

    boolean existsByCvu(String cvu);

    boolean existsByAlias(String alias);

    /**
     * Lee la cuenta bloqueando la fila hasta el fin de la transaccion (SELECT ... FOR UPDATE): dos
     * operaciones de dinero sobre la misma cuenta se ejecutan una despues de la otra.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Account a where a.id = :id")
    Optional<Account> findByIdForUpdate(@Param("id") Long id);
}
