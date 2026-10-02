package com.dmh.accounts.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dmh.accounts.model.Account;

public interface AccountRepository extends JpaRepository<Account, Long> {

    Optional<Account> findByUserId(Long userId);

    boolean existsByCvu(String cvu);

    boolean existsByAlias(String alias);
}
